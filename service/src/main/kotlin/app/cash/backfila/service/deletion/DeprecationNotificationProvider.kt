package app.cash.backfila.service.deletion

import java.time.Duration

enum class NotificationDecision {
  EXPLICIT_DELETE_BY,
  COMPLETE_RUN,
  PAUSED_RUN,
  DEFAULT_CREATION,
}

data class DeprecationMessage(
  val delay: Duration,
  val message: String,
  val messageLastUser: Boolean = false,
  val repeated: Boolean = false,
)

interface DeprecationMessageBuilder {
  val notifications: Map<NotificationDecision, List<DeprecationMessage>>
  val defaultDelayDays: Map<NotificationDecision, Duration>
}

interface DeprecationNotificationProvider {
  fun getNotificationConfig(): DeprecationMessageBuilder
}
