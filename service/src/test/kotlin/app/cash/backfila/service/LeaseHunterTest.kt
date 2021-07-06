package app.cash.backfila.service

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors.ENVOY
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.scheduler.LeaseHunter
import com.google.inject.Module
import javax.inject.Inject
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class LeaseHunterTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction
  @Inject lateinit var createBackfillAction: CreateBackfillAction
  @Inject lateinit var startBackfillAction: StartBackfillAction
  @Inject lateinit var scope: ActionScope
  @Inject lateinit var leaseHunter: LeaseHunter
  @Inject lateinit var clock: FakeClock

  @Test
  fun noBackfillsNoLease() {
    assertThat(leaseHunter.hunt()).isEmpty()
  }

  @Test
  fun pausedBackfillNotLeased() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null
              )
            )
          )
          .connector_type(ENVOY)
          .build()
      )
    }
    scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )
    }
    assertThat(leaseHunter.hunt()).isEmpty()
  }

  @Test
  fun runningBackfillLeased() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null
              )
            )
          )
          .connector_type(ENVOY)
          .build()
      )
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )

      val id = response.backfill_run_id
      startBackfillAction.start(id, StartBackfillRequest())
    }

    val runners = leaseHunter.hunt()
    assertThat(runners).hasSize(1)
    val runner = runners.single()
    assertThat(runner.backfillName).isEqualTo("ChickenSandwich")

    val runners2 = leaseHunter.hunt()
    assertThat(runners2).hasSize(1)
    val runner2 = runners2.single()

    assertThat(runner.partitionId).isNotEqualTo(runner2.partitionId)
  }

  @Test
  fun activeLeaseNotStolen() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null
              )
            )
          )
          .connector_type(ENVOY)
          .build()
      )
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )

      val id = response.backfill_run_id
      startBackfillAction.start(id, StartBackfillRequest())
    }

    val runners = leaseHunter.hunt()
    assertThat(runners).hasSize(1)

    val runners2 = leaseHunter.hunt()
    assertThat(runners2).hasSize(1)

    assertThat(runners.single().partitionId).isNotEqualTo(runners2.single().partitionId)
    assertThat(leaseHunter.hunt()).isEmpty()

    // Advance past the lease expiry.
    clock.add(LeaseHunter.LEASE_DURATION.plusSeconds(1))
    assertThat(leaseHunter.hunt()).hasSize(1)
  }
}
