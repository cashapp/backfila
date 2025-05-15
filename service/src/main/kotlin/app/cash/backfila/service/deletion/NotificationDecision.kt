package app.cash.backfila.service.deletion

enum class NotificationDecision(val emoji: String) {
  NONE(""),
  NOTIFY_EXPIRED("⏰"), // For post-deletion notifications
}
