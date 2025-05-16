package app.cash.backfila.service.deletion

import java.time.Duration

interface DeprecationNotificationProvider {
  fun getNotificationConfig(): DeprecationNotificationConfig
}

data class DeprecationNotificationConfig(
  val defaultDeleteByDuration: Duration,
  val completeRunRetention: Duration,
  val pausedRunRetention: Duration,
  val registerRetention: Duration,
  val monthlyRemindersPhase: Duration,
)
