package com.squareup.backfila.service

import com.google.inject.Module
import com.squareup.backfila.BackfilaTestingModule
import com.squareup.backfila.api.ConfigureServiceAction
import com.squareup.backfila.client.Connectors.ENVOY
import com.squareup.backfila.dashboard.CreateBackfillAction
import com.squareup.backfila.dashboard.CreateBackfillRequest
import com.squareup.backfila.dashboard.StartBackfillAction
import com.squareup.backfila.dashboard.StartBackfillRequest
import com.squareup.backfila.fakeCaller
import com.squareup.protos.backfila.service.ConfigureServiceRequest
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

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
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("ChickenSandwich", "Description", listOf(), null,
                  null, false)))
          .connector_type(ENVOY)
          .build())
    }
    scope.fakeCaller(user = "molly") {
      createBackfillAction.create("deep-fryer",
          CreateBackfillRequest("ChickenSandwich"))
    }
    assertThat(leaseHunter.hunt()).isEmpty()
  }

  @Test
  fun runningBackfillLeased() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("ChickenSandwich", "Description", listOf(), null,
                  null, false)))
          .connector_type(ENVOY)
          .build())
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create("deep-fryer",
          CreateBackfillRequest("ChickenSandwich"))

      val id = response.id
      startBackfillAction.start(id, StartBackfillRequest())
    }

    val runners = leaseHunter.hunt()
    assertThat(runners).hasSize(1)
    val runner = runners.single()
    assertThat(runner.backfillName).isEqualTo("ChickenSandwich")

    val runners2 = leaseHunter.hunt()
    assertThat(runners2).hasSize(1)
    val runner2 = runners2.single()

    assertThat(runner.instanceId).isNotEqualTo(runner2.instanceId)
  }

  @Test
  fun activeLeaseNotStolen() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("ChickenSandwich", "Description", listOf(), null,
                  null, false)))
          .connector_type(ENVOY)
          .build())
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create("deep-fryer",
          CreateBackfillRequest("ChickenSandwich"))

      val id = response.id
      startBackfillAction.start(id, StartBackfillRequest())
    }

    val runners = leaseHunter.hunt()
    assertThat(runners).hasSize(1)

    val runners2 = leaseHunter.hunt()
    assertThat(runners2).hasSize(1)

    assertThat(runners.single().instanceId).isNotEqualTo(runners2.single().instanceId)
    assertThat(leaseHunter.hunt()).isEmpty()

    // Advance past the lease expiry.
    clock.add(LeaseHunter.LEASE_DURATION.plusSeconds(1))
    assertThat(leaseHunter.hunt()).hasSize(1)
  }
}
