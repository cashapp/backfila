package app.cash.backfila.service.deletion

import java.time.Duration
import misk.config.Config

data class DeleteByNotificationConfig(
    // Default delete_by duration for backfills without an explicit date
    val defaultDeleteByDuration: Duration = Duration.ofDays(180), // 6 months
    
    // Pre-deletion notification thresholds
    val promptAfterLastSuccessfulRun: Duration = Duration.ofDays(30),
    val promptAfterFailedOrNoRuns: Duration = Duration.ofDays(90),
    
    // Pre-deletion notification stages
    val preDeleteStages: List<NotificationStage> = listOf(
        NotificationStage(Duration.ofDays(90), Duration.ofDays(30)),  // 3 months out: Monthly
        NotificationStage(Duration.ofDays(60), Duration.ofDays(7)),   // 2 months out: Weekly
        NotificationStage(Duration.ofDays(30), Duration.ofDays(1))    // 1 month out: Daily
    ),

    // Post-deletion notification configuration
    val postDeleteNotifications: PostDeleteNotifications = PostDeleteNotifications(
        initialDelay = Duration.ofDays(1),    // First notification 1 day after delete_by
        followUpDelays = listOf(              // Additional notifications after
            Duration.ofDays(7),               // 1 week
            Duration.ofDays(30),              // 1 month
            Duration.ofDays(90)               // 3 months
        ),
        maxAge = Duration.ofDays(180)         // Stop notifications after 6 months
    )
) : Config

data class NotificationStage(
    val threshold: Duration,
    val frequency: Duration
)

data class PostDeleteNotifications(
    val initialDelay: Duration,      // How long after delete_by to send first notification
    val followUpDelays: List<Duration>, // When to send follow-up notifications
    val maxAge: Duration            // Stop notifications after this duration past delete_by
)

enum class NotificationDecision(val emoji: String) {
    NONE(""),
    NOTIFY_INFO("ℹ️"),
    NOTIFY_WARNING("⚠️"),
    NOTIFY_URGENT("🚨"),
    NOTIFY_EXPIRED("⏰")  // For post-deletion notifications
}