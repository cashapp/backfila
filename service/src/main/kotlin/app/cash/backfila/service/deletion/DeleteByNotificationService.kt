package app.cash.backfila.service.deletion

import app.cash.backfila.service.persistence.DbRegisteredBackfill
import com.google.common.util.concurrent.AbstractExecutionThreadService
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton
import wisp.logging.getLogger

/**
 * Service that periodically checks for backfills approaching their delete_by date
 * and sends notifications according to the configured schedule.
 */
@Singleton
class DeleteByNotificationService @Inject constructor(
    private val notificationHelper: DeleteByNotificationHelper,
    private val clock: Clock
) : AbstractExecutionThreadService() {
    @Volatile private var running = false
    private val random = Random()

    override fun startUp() {
        running = true
        logger.info { "Starting DeleteByNotificationService" }
    }

    override fun run() {
        while (running) {
            try {
                checkBackfills()
            } catch (e: Exception) {
                logger.error(e) { "Error checking backfills for deletion notifications" }
            }

            // Sleep for an hour plus random jitter to avoid clustering
            Thread.sleep(HOUR_IN_MILLIS + random.nextInt(JITTER_RANGE_MILLIS))
        }
    }

    override fun triggerShutdown() {
        running = false
    }

    private fun checkBackfills() {
        notificationHelper.getBackfillsNeedingNotification().forEach { backfill ->
            val decision = notificationHelper.evaluateBackfill(backfill)
            
            if (decision != NotificationDecision.NONE) {
                val channel = notificationHelper.determineNotificationChannel(backfill)
                
                if (isBusinessHours(backfill)) {
                    try {
                        notificationHelper.sendNotification(
                            backfill = backfill,
                            decision = decision,
                            channel = channel
                        )
                        logger.info { "Sent deletion notification for backfill: ${backfill.name}" }
                    } catch (e: Exception) {
                        logger.error(e) { "Failed to send notification for backfill: ${backfill.name}" }
                    }
                } else {
                    logger.info { "Skipping notification for ${backfill.name} outside business hours" }
                }
            }
        }
    }
    
    private fun isBusinessHours(backfill: DbRegisteredBackfill): Boolean {
        val timeZone = ZoneId.of("America/Los_Angeles")
        
        val localTime = clock.instant().atZone(timeZone)
        val hour = localTime.hour
        
        return hour in 9..17 && // 9 AM to 5 PM
               localTime.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }

    companion object {
        private val logger = getLogger<DeleteByNotificationService>()
        private const val HOUR_IN_MILLIS = 3_600_000L // 1 hour
        private const val JITTER_RANGE_MILLIS = 300_000 // 5 minutes
    }
}