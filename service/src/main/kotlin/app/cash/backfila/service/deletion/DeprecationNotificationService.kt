package app.cash.backfila.service.deletion

import app.cash.backfila.service.persistence.DbRegisteredBackfill
import com.google.common.util.concurrent.AbstractExecutionThreadService
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneOffset
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
      Thread.sleep(FOUR_HOURS_IN_MILLIS + random.nextInt(JITTER_RANGE_MILLIS))
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
    val creationTime = registeredBackfill.created_at
    val currentTime = clock.instant()

    // Avoid weekends in UTC
    if (currentTime.atZone(ZoneOffset.UTC).dayOfWeek !in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
      return false
    }

    // Keep the same hour of day as when the backfill was created
    val creationHour = creationTime.atZone(ZoneOffset.UTC).hour
    val currentHour = currentTime.atZone(ZoneOffset.UTC).hour

    // Check if current hour is within +/- 2 hour of creation time
    return currentHour in (creationHour - 2)..(creationHour + 2)
  }

  companion object {
    private val logger = getLogger<DeprecationNotificationService>()
    private const val FOUR_HOURS_IN_MILLIS = 14_400_000L // 4 hours
    private const val JITTER_RANGE_MILLIS = 300_000 // 5 minutes
  }
}
