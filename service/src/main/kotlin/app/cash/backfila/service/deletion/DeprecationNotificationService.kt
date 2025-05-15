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
class DeprecationNotificationService @Inject constructor(
  private val notificationHelper: DeprecationNotificationHelper,
  private val clock: Clock,
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
    notificationHelper.getRegisteredBackfillsForNotification().forEach { registeredBackfill ->
      val decision = notificationHelper.evaluateRegisteredBackfill(registeredBackfill)

      if (decision != NotificationDecision.NONE && registeredBackfill.service.slack_channel != null) {
        if (isBusinessHours(registeredBackfill)) {
          try {
            notificationHelper.sendNotification(
              decision = decision,
              channel = registeredBackfill.service.slack_channel!!,
            )
            logger.info { "Sent deletion notification for backfill: ${registeredBackfill.name}" }
          } catch (e: Exception) {
            logger.error(e) { "Failed to send notification for backfill: ${registeredBackfill.name}" }
          }
        } else {
          logger.info { "Skipping notification for ${registeredBackfill.name} outside business hours" }
        }
      }
    }
  }

  private fun isBusinessHours(registeredBackfill: DbRegisteredBackfill): Boolean {
    val timeZone = ZoneId.of("America/Los_Angeles")

    val localTime = clock.instant().atZone(timeZone)
    val hour = localTime.hour

    return hour in 9..17 && // 9 AM to 5 PM
      localTime.dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
  }

  companion object {
    private val logger = getLogger<DeprecationNotificationService>()
    private const val HOUR_IN_MILLIS = 10_800_000L // 3 hours
    private const val JITTER_RANGE_MILLIS = 300_000 // 5 minutes
  }
}
