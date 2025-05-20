package app.cash.backfila.service.deletion

import app.cash.backfila.service.listener.SlackHelper
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbDeprecationReminder
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.DeprecationReminderQuery
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery

@Singleton
class DeprecationNotificationHelper @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val slackHelper: SlackHelper,
  private val notificationProvider: DeprecationNotificationProvider,
  private val clock: Clock,
) {
  private val daysInMonth = 30L

  fun getRegisteredBackfillsForNotification(): List<DbRegisteredBackfill> {
    return transacter.transaction { session ->
      queryFactory.newQuery<RegisteredBackfillQuery>()
        .active()
        .list(session)
    }
  }

  fun notifyRegisteredBackfill(registeredBackfill: DbRegisteredBackfill): NotificationDecision? {
    return transacter.transaction { session ->
      val now = clock.instant()
      val config = notificationProvider.getNotificationConfig()

      // If service has not been registered for 90 days, skip notification
      val service = registeredBackfill.service // This should be loaded due to JPA relationship
      val lastRegisteredAt = service.last_registered_at
      if (lastRegisteredAt != null &&
        Duration.between(lastRegisteredAt, now) > Duration.ofDays(daysInMonth * 3)
      ) {
        return@transaction null
      }

      if (!isBusinessHours(registeredBackfill)) {
        return@transaction null
      }

      val deleteByDates = mutableListOf<Pair<NotificationDecision, Instant>>()

      // Explicit delete-by date
      registeredBackfill.delete_by?.let { deleteBy ->
        deleteByDates.add(NotificationDecision.EXPLICIT_DELETE_BY to deleteBy)
      }

      // Default creation-based delete-by
      val defaultDeleteBy = registeredBackfill.created_at
        .plus(config.defaultDelayDays[NotificationDecision.DEFAULT_CREATION]!!)
      deleteByDates.add(NotificationDecision.DEFAULT_CREATION to defaultDeleteBy)

      // Get the latest run and its status
      val latestRun = queryFactory.newQuery<BackfillRunQuery>()
        .registeredBackfillId(registeredBackfill.id)
        .orderByUpdatedAtDesc()
        .apply {
          maxRows = 1
        }
        .list(session)
        .firstOrNull()

      // Add run-based delete_by dates to deleteByDates
      latestRun?.let {
        val runDate = it.created_at
        when (it.state) {
          BackfillState.COMPLETE -> deleteByDates.add(
            NotificationDecision.COMPLETE_RUN to runDate.plus(config.defaultDelayDays[NotificationDecision.COMPLETE_RUN]!!),
          )
          BackfillState.PAUSED -> deleteByDates.add(
            NotificationDecision.PAUSED_RUN to runDate.plus(config.defaultDelayDays[NotificationDecision.PAUSED_RUN]!!),
          )
          else -> null
        }
      }

      // Find the latest delete-by date and its associated decision
      val (effectiveDecision, effectiveDeleteBy) = deleteByDates.maxByOrNull { it.second }
        ?: return@transaction null

      // Don't send notifications before the delete_by date
      val timeUntilDeletion = Duration.between(now, effectiveDeleteBy)
      if (!timeUntilDeletion.isNegative) {
        return@transaction null
      }

      // We're past the delete_by date, determine notification frequency
      val timeSinceDeletion = Duration.between(effectiveDeleteBy, now)
      val notifications = config.notifications[effectiveDecision] ?: emptyList()

      // Find the appropriate notification based on timeSinceDeletion
      val appropriateNotification = notifications
        .sortedBy { it.delay } // Sort by delay to get earliest first
        .findLast { notification ->
          // Find the last notification whose delay is less than or equal to timeSinceDeletion
          timeSinceDeletion >= notification.delay
        } ?: return@transaction null

      // Get the last notification sent
      val lastReminder = queryFactory.newQuery<DeprecationReminderQuery>()
        .registeredBackfillId(registeredBackfill.id)
        .orderByCreatedAtDesc()
        .apply { maxRows = 1 }
        .list(session)
        .firstOrNull()

      // Check if we should send this notification
      val shouldSendNotification = when {
        lastReminder == null -> {
          // No previous reminder, should send
          true
        }
        appropriateNotification.repeated -> {
          // For repeated notifications, check if enough time has passed since last reminder
          Duration.between(lastReminder.created_at, now) >= appropriateNotification.delay
        }
        else -> {
          // For non-repeated notifications, check if this specific type hasn't been sent
          !(lastReminder.message_last_user == appropriateNotification.messageLastUser && !lastReminder.repeated)
        }
      }

      if (shouldSendNotification) {
        val message = generateNotificationMessage(appropriateNotification, registeredBackfill)
        sendNotification(message, registeredBackfill.service.slack_channel)
        // Log to deprecation reminder table
        val reminder = DbDeprecationReminder(
          registeredBackfill.id,
          appropriateNotification.messageLastUser, appropriateNotification.repeated,
          now,
        )
        session.save(reminder)
        return@transaction effectiveDecision
      }

      return@transaction null
    }
  }

  // Separate message generation function that doesn't need DB access
  private fun generateNotificationMessage(
    notification: DeprecationMessage,
    registeredBackfill: DbRegisteredBackfill,
  ): String {
    // Use the configured message directly
    val baseMessage = notification.message

    // Add metadata about the backfill
    val metadata = buildString {
      appendLine()
      appendLine("*Additional Information:*")
      appendLine("• Backfill: `${registeredBackfill.name}`")

      // Add action items
      appendLine()
      appendLine("*Actions Required:*")
      appendLine("1. Review if this backfill is still needed")
      appendLine("2. Update the `@DeleteBy` annotation with a new date")
      appendLine("3. Or run the backfill again to extend its lifetime")
    }

    return baseMessage + metadata
  }

  // Update send notification to use the new message generation
  fun sendNotification(
    message: String,
    channel: String?,
  ) {
    if (channel == null) {
      // logger.warn { "No Slack channel specified for notification. Skipping." }
      return
    }
    // Send to Slack
    slackHelper.sendDeletionNotification(message, channel)
  }

  private fun isBusinessHours(registeredBackfill: DbRegisteredBackfill): Boolean {
    val creationTime = registeredBackfill.created_at
    val currentTime = clock.instant()

    // Avoid weekends in UTC
    if (currentTime.atZone(ZoneOffset.UTC).dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
      return false
    }

    // Keep the same hour of day as when the backfill was created
    val creationHour = creationTime.atZone(ZoneOffset.UTC).hour
    val currentHour = currentTime.atZone(ZoneOffset.UTC).hour

    return creationHour == currentHour
  }
}
