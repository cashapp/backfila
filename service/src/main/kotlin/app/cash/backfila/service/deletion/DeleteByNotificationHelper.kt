package app.cash.backfila.service.deletion

import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.EventLogQuery
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.listener.SlackHelper
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import misk.hibernate.Id
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import wisp.logging.getLogger

@Singleton
class DeleteByNotificationHelper @Inject constructor(
    @BackfilaDb private val transacter: Transacter,
    private val queryFactory: Query.Factory,
    private val slackHelper: SlackHelper,
    private val backfilaConfig: BackfilaConfig,
    private val clock: Clock
) {
    fun getBackfillsNeedingNotification(): List<DbRegisteredBackfill> {
        return transacter.transaction { session ->
            // Get all active backfills
            val backfills = queryFactory.newQuery<RegisteredBackfillQuery>()
                .active()
                .list(session)

            // For backfills without a delete_by date, set it based on creation time
            backfills.forEach { backfill ->
                if (backfill.delete_by == null) {
                    backfill.delete_by = backfill.created_at.plus(
                        backfilaConfig.delete_by_notification.defaultDeleteByDuration
                    )
                    // Save the updated backfill
                    session.save(backfill)
                }
            }
            backfills
        }
    }

    fun evaluateBackfill(backfill: DbRegisteredBackfill): NotificationDecision {
        val deleteBy = backfill.delete_by
            ?: return NotificationDecision.NONE

        return transacter.transaction { session ->
            // Get relevant events
            val events = queryFactory.newQuery<EventLogQuery>()
                .backfillRunId(backfill.id as Id<DbBackfillRun>)
                .list(session)

            val lastSuccessfulRun = events
                .filter { it.type == DbEventLog.Type.NOTIFICATION && it.message == "COMPLETED" }
                .maxByOrNull { it.created_at }

            val lastNotification = events
                .filter { it.type == DbEventLog.Type.NOTIFICATION }
                .maxByOrNull { it.created_at }

            // Calculate time until/since deletion
            val now = clock.instant()
            val timeUntilDeletion = Duration.between(now, deleteBy)

            // If we're past the delete_by date
            if (timeUntilDeletion.isNegative) {
                val timeSinceDeletion = Duration.between(deleteBy, now)
                return@transaction evaluatePostDeleteNotification(timeSinceDeletion, lastNotification)
            }

            // Find appropriate notification stage
            val stage = backfilaConfig.delete_by_notification.preDeleteStages.find {
                timeUntilDeletion <= it.threshold
            } ?: return@transaction NotificationDecision.NONE

            // Check if we should notify based on the stage frequency
            val shouldNotify = lastNotification?.let {
                Duration.between(it.created_at, now) >= stage.frequency
            } ?: true

            if (!shouldNotify) {
                return@transaction NotificationDecision.NONE
            }

            // Determine notification urgency based on run status
            when {
                lastSuccessfulRun == null ||
                  Duration.between(lastSuccessfulRun.created_at, now) > backfilaConfig.delete_by_notification.promptAfterFailedOrNoRuns ->
                    NotificationDecision.NOTIFY_URGENT

                Duration.between(lastSuccessfulRun.created_at, now) > backfilaConfig.delete_by_notification.promptAfterLastSuccessfulRun ->
                    NotificationDecision.NOTIFY_WARNING

                else -> NotificationDecision.NOTIFY_INFO
            }
        }
    }

    private fun evaluatePostDeleteNotification(
        timeSinceDeletion: Duration,
        lastNotification: DbEventLog?
    ): NotificationDecision {
        val config = backfilaConfig.delete_by_notification.postDeleteNotifications

        // Stop notifications if we're past maxAge
        if (timeSinceDeletion > config.maxAge) {
            return NotificationDecision.NONE
        }

        // If we haven't sent any notifications yet, check initial delay
        if (lastNotification == null) {
            return if (timeSinceDeletion >= config.initialDelay) {
                NotificationDecision.NOTIFY_EXPIRED
            } else {
                NotificationDecision.NONE
            }
        }

        // Find the next notification delay that's applicable
        val nextDelay = config.followUpDelays.find { delay ->
            timeSinceDeletion <= delay &&
              Duration.between(lastNotification.created_at, clock.instant()) >= delay
        }

        return if (nextDelay != null) NotificationDecision.NOTIFY_EXPIRED else NotificationDecision.NONE
    }

    fun sendNotification(
        backfill: DbRegisteredBackfill,
        decision: NotificationDecision,
        channel: String
    ) {
        val message = generateNotificationMessage(backfill, decision)

        // Send to Slack
        slackHelper.sendDeletionNotification(message, channel)

        // Record notification in event_logs
        transacter.transaction { session ->
            session.save(
                DbEventLog(
                    backfill_run_id = backfill.id as Id<DbBackfillRun>,
                    partition_id = null,
                    type = DbEventLog.Type.NOTIFICATION,
                    message = "Deletion notification sent to $channel",
                    extra_data = message
                )
            )
        }
    }

    fun determineNotificationChannel(backfill: DbRegisteredBackfill): String {
        // TODO: Make this configurable or determine from backfill metadata
        return "#backfila-notifications"
    }

    private fun generateNotificationMessage(
        backfill: DbRegisteredBackfill,
        decision: NotificationDecision
    ): String {
        val deleteBy = backfill.delete_by!!
        val daysUntilDeletion = ChronoUnit.DAYS.between(clock.instant(), deleteBy)

        val lastInteraction = transacter.transaction { session ->
            queryFactory.newQuery<EventLogQuery>()
                .backfillRunId(backfill.id as Id<DbBackfillRun>)
                .list(session)
                .maxByOrNull { it.created_at }
        }

        return """
            |${decision.emoji} *Backfill Deletion Notice*
            |Backfill `${backfill.name}` is scheduled for deletion on ${deleteBy}.
            |
            |• Days until deletion: $daysUntilDeletion
            |• Last activity: ${lastInteraction?.created_at ?: "Never"}
            |${if (lastInteraction?.user != null) "• Last touched by: ${lastInteraction.user}" else ""}
            |
            |To extend this backfill's lifetime, please:
            |1. Review if this backfill is still needed
            |2. Update the `@DeleteBy` annotation with a new date if needed
            |
            |_Note: Any activity on this backfill will reset the notification schedule._
        """.trimMargin()
    }

    companion object {
        private val logger = getLogger<DeleteByNotificationHelper>()
    }
}