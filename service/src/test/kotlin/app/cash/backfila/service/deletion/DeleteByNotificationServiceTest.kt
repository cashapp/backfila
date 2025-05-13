package app.cash.backfila.service.deletion

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbEventLog
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.DbService
import com.google.inject.Module
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DeleteByNotificationServiceTest {
    @MiskTestModule
    val module: Module = BackfilaTestingModule()

    @Inject @BackfilaDb lateinit var transacter: Transacter
    @Inject lateinit var notificationHelper: DeleteByNotificationHelper
    @Inject lateinit var clock: FakeClock

    init {
        clock.setNow(Instant.parse("2024-01-01T10:00:00Z"))
    }

    private fun createBackfill(deleteBy: Instant?): DbRegisteredBackfill {
        return transacter.transaction { session ->
            val service = session.save(DbService(name = "test-service"))
            session.save(DbRegisteredBackfill(
                service_id = service.id,
                name = "test-backfill",
                delete_by = deleteBy
            ))
        }
    }

    private fun createEvent(
        backfill: DbRegisteredBackfill,
        type: String,
        message: String,
        timestamp: Instant
    ) {
        transacter.transaction { session ->
            session.save(DbEventLog(
                backfill_run_id = backfill.id,
                type = type,
                message = message,
                created_at = timestamp
            ))
        }
    }

    @Test
    fun `no notification needed when no delete_by date`() {
        val backfill = createBackfill(null)
        val decision = notificationHelper.evaluateBackfill(backfill)
        assertThat(decision).isEqualTo(NotificationDecision.NONE)
    }

    @Test
    fun `urgent notification when no successful runs`() {
        val backfill = createBackfill(clock.instant().plus(Duration.ofDays(45)))
        val decision = notificationHelper.evaluateBackfill(backfill)
        assertThat(decision).isEqualTo(NotificationDecision.NOTIFY_URGENT)
    }

    @Test
    fun `warning notification when last successful run is old`() {
        val backfill = createBackfill(clock.instant().plus(Duration.ofDays(45)))
        createEvent(
            backfill = backfill,
            type = "STATE_CHANGE",
            message = "COMPLETED",
            timestamp = clock.instant().minus(Duration.ofDays(35))
        )
        val decision = notificationHelper.evaluateBackfill(backfill)
        assertThat(decision).isEqualTo(NotificationDecision.NOTIFY_WARNING)
    }

    @Test
    fun `info notification when recent successful run`() {
        val backfill = createBackfill(clock.instant().plus(Duration.ofDays(45)))
        createEvent(
            backfill = backfill,
            type = "STATE_CHANGE",
            message = "COMPLETED",
            timestamp = clock.instant().minus(Duration.ofDays(15))
        )
        val decision = notificationHelper.evaluateBackfill(backfill)
        assertThat(decision).isEqualTo(NotificationDecision.NOTIFY_INFO)
    }

    @Test
    fun `respects notification frequency stages`() {
        val backfill = createBackfill(clock.instant().plus(Duration.ofDays(45)))
        
        // Create a notification 5 days ago
        createEvent(
            backfill = backfill,
            type = "NOTIFICATION",
            message = "Previous notification",
            timestamp = clock.instant().minus(Duration.ofDays(5))
        )

        // We're in the 60-day stage which has weekly frequency
        // Since last notification was 5 days ago, we shouldn't notify yet
        val decision = notificationHelper.evaluateBackfill(backfill)
        assertThat(decision).isEqualTo(NotificationDecision.NONE)
    }
}