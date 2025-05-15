package app.cash.backfila.service.deletion

import app.cash.backfila.service.listener.SlackHelper
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.EventLogQuery
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
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
  // Data class to hold all the info needed for message generation
  data class NotificationContext(
    val backfill: DbRegisteredBackfill,
    val latestRun: DbBackfillRun?,
    val effectiveDeleteBy: Instant,
    val deleteByReason: DeleteByReason,
  )

  // Enum to clearly indicate why a date was chosen
  enum class DeleteByReason {
    EXPLICIT_DELETE_BY,
    SUCCESSFUL_RUN,
    PAUSED_RUN,
    DEFAULT_CREATION,
  }

  fun getRegisteredBackfillsForNotification(): List<DbRegisteredBackfill> {
    return transacter.transaction { session ->
      queryFactory.newQuery<RegisteredBackfillQuery>()
        .active()
        .list(session)
    }
  }

  fun evaluateRegisteredBackfill(registeredBackfill: DbRegisteredBackfill): NotificationDecision {
    return transacter.transaction { session ->
      val now = clock.instant()
      val config = notificationProvider.getNotificationConfig()

      // If service has not been registered for 90 days, skip notification
      val service = registeredBackfill.service // This should be loaded due to JPA relationship
      val lastRegisteredAt = service.last_registered_at
      if (lastRegisteredAt != null &&
        Duration.between(lastRegisteredAt, now) > config.registerRetention
      ) {
        return@transaction NotificationDecision.NONE
      }

      val defaultDeleteBy = registeredBackfill.created_at.plus(config.defaultDeleteByDuration)

      // Get the latest run and its status
      val latestRun = queryFactory.newQuery<BackfillRunQuery>()
        .registeredBackfillId(registeredBackfill.id)
        .orderByUpdatedAtDesc()
        .apply {
          maxRows = 1
        }
        .list(session)
        .firstOrNull()

      // Calculate delete_by date based on latest run
      val runBasedDeleteBy = latestRun?.let {
        val runDate = it.created_at
        when (it.state) {
          BackfillState.COMPLETE -> runDate.plus(config.completeRunRetention)
          BackfillState.PAUSED -> runDate.plus(config.pausedRunRetention)
          else -> null
        }
      }

      // Determine the maximum date to use and why
      val (effectiveDeleteBy, deleteByReason) = when {
        registeredBackfill.delete_by != null &&
          (registeredBackfill.delete_by == listOfNotNull(registeredBackfill.delete_by, defaultDeleteBy, runBasedDeleteBy).maxOrNull()) ->
          registeredBackfill.delete_by!! to DeleteByReason.EXPLICIT_DELETE_BY

        runBasedDeleteBy != null &&
          (runBasedDeleteBy == listOfNotNull(registeredBackfill.delete_by, defaultDeleteBy, runBasedDeleteBy).maxOrNull()) ->
          if (latestRun.state == BackfillState.COMPLETE) {
            runBasedDeleteBy to DeleteByReason.SUCCESSFUL_RUN
          } else {
            runBasedDeleteBy to DeleteByReason.PAUSED_RUN
          }

        else -> defaultDeleteBy to DeleteByReason.DEFAULT_CREATION
      }

      // Don't send notifications before the delete_by date
      val timeUntilDeletion = Duration.between(now, effectiveDeleteBy)
      if (!timeUntilDeletion.isNegative) {
        return@transaction NotificationDecision.NONE
      }

      // We're past the delete_by date, determine notification frequency
      val timeSinceDeletion = Duration.between(effectiveDeleteBy, now)

      // Get the last notification sent
      // First get all backfill run IDs for this registered backfill
      val backfillRunIds = queryFactory.newQuery<BackfillRunQuery>()
        .registeredBackfillId(registeredBackfill.id)
        .list(session)
        .map { it.id }

      // Then if we have any runs, query event logs for notifications across all these runs
      val lastNotification = if (backfillRunIds.isNotEmpty()) {
        queryFactory.newQuery<EventLogQuery>()
          .backfillRunIdIn(backfillRunIds) // We'd need to add this method to EventLogQuery
          .type(DbEventLog.Type.NOTIFICATION)
          .orderByUpdatedAtDesc()
          .apply {
            maxRows = 1
          }
          .list(session)
          .firstOrNull()
      } else {
        null
      }

      // First 3 months: monthly reminders
      if (timeSinceDeletion <= config.monthlyRemindersPhase) {
        val shouldSendMonthly = lastNotification?.let {
          Duration.between(it.created_at, now) >= Duration.ofDays(30)
        } ?: true

        if (shouldSendMonthly) {
          val context = NotificationContext(
            backfill = registeredBackfill,
            latestRun = latestRun,
            effectiveDeleteBy = effectiveDeleteBy,
            deleteByReason = deleteByReason,
          )

          currentNotificationContext = context
          return@transaction NotificationDecision.NOTIFY_EXPIRED
        }
      }
      // After 3 months: weekly reminders
      else {
        val shouldSendWeekly = lastNotification?.let {
          Duration.between(it.created_at, now) >= Duration.ofDays(7)
        } ?: true

        if (shouldSendWeekly) {
          val context = NotificationContext(
            backfill = registeredBackfill,
            latestRun = latestRun,
            effectiveDeleteBy = effectiveDeleteBy,
            deleteByReason = deleteByReason,
          )

          currentNotificationContext = context
          return@transaction NotificationDecision.NOTIFY_EXPIRED
        }
      }

      NotificationDecision.NONE
    }
  }

  // Separate message generation function that doesn't need DB access
  private fun generateNotificationMessage(
    decision: NotificationDecision,
    context: NotificationContext,
  ): String {
    val now = clock.instant()
    val daysSinceDeletion = ChronoUnit.DAYS.between(context.effectiveDeleteBy, now)

    val deleteByReasonMessage = when (context.deleteByReason) {
      DeleteByReason.EXPLICIT_DELETE_BY -> "explicitly set delete_by date"
      DeleteByReason.SUCCESSFUL_RUN -> "30 days after last successful run"
      DeleteByReason.PAUSED_RUN -> "90 days after last failed run"
      DeleteByReason.DEFAULT_CREATION -> "6 months after creation"
    }

    return """
        |${decision.emoji} *Backfill Deletion Reminder*
        |Backfill `${context.backfill.name}` was due for deletion on ${context.effectiveDeleteBy}.
        |
        |• Days since deletion date: $daysSinceDeletion
        |• Deletion date determined by: $deleteByReasonMessage
        |${context.latestRun?.let { "• Last run status: ${it.state} on ${it.created_at}" } ?: "• No runs found"}
        |
        |To keep this backfill:
        |1. Review if this backfill is still needed
        |2. Update the `@DeleteBy` annotation with a new date
        |3. Or run the backfill again to extend its lifetime
        |
        |_This reminder will be sent monthly for 3 months, then weekly until action is taken._
    """.trimMargin()
  }

  // Update send notification to use the new message generation
  fun sendNotification(
    decision: NotificationDecision,
    channel: String,
  ) {
    val message = generateNotificationMessage(decision, currentNotificationContext!!)

    // Send to Slack
    slackHelper.sendDeletionNotification(message, channel)

    // Record notification in event_logs for the latest run if it exists
    currentNotificationContext?.latestRun?.let { latestRun ->
      transacter.transaction { session ->
        session.save(
          DbEventLog(
            backfill_run_id = latestRun.id,
            partition_id = null,
            type = DbEventLog.Type.NOTIFICATION,
            message = "Deletion notification sent to $channel",
            extra_data = message,
          ),
        )
      }
    }

    // Clear the context after use
    currentNotificationContext = null
  }

  // Add property to store context between evaluate and send
  private var currentNotificationContext: NotificationContext? = null
}
