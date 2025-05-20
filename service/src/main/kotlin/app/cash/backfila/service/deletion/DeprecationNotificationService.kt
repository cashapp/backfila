package app.cash.backfila.service.deletion

import com.google.common.util.concurrent.AbstractExecutionThreadService
import java.time.Clock
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
      notificationHelper.notifyRegisteredBackfill(registeredBackfill)
    }
  }

  companion object {
    private val logger = getLogger<DeprecationNotificationService>()
    private const val FOUR_HOURS_IN_MILLIS = 14_400_000L // 4 hours
    private const val JITTER_RANGE_MILLIS = 300_000 // 5 minutes
  }
}
