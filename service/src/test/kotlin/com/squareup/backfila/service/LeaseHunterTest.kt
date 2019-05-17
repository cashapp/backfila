package com.squareup.backfila.service

import com.google.inject.Module
import com.squareup.backfila.BackfilaTestingModule
import com.squareup.backfila.api.ConfigureServiceAction
import com.squareup.backfila.dashboard.CreateBackfillAction
import com.squareup.backfila.dashboard.CreateBackfillRequest
import com.squareup.backfila.dashboard.StartBackfillAction
import com.squareup.backfila.dashboard.StartBackfillRequest
import com.squareup.backfila.dashboard.StopBackfillAction
import com.squareup.backfila.fakeCaller
import com.squareup.protos.backfila.service.ConfigureServiceRequest
import com.squareup.protos.backfila.service.ServiceType
import misk.hibernate.Query
import misk.hibernate.Transacter
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
  @Inject lateinit var stopBackfillAction: StopBackfillAction
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var scope: ActionScope
  @Inject lateinit var leaseHunter: LeaseHunter
  @Inject @BackfilaDb lateinit var transacter: Transacter
  @Inject lateinit var clock: FakeClock

  @Test
  fun noBackfillsNoLease() {
    assertThat(leaseHunter.hunt()).isEmpty()
  }

  @Test
  fun pausedBackfillNotLeased() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("ChickenSandwich", listOf(), null, null, false)),
          ServiceType.SQUARE_DC))
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create("deep-fryer",
          CreateBackfillRequest("ChickenSandwich"))
    }
    assertThat(leaseHunter.hunt()).isEmpty()
  }

  @Test
  fun runningBackfillLeased() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("ChickenSandwich", listOf(), null, null, false)),
          ServiceType.SQUARE_DC))
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create("deep-fryer",
          CreateBackfillRequest("ChickenSandwich"))

      val id = response.headers["Location"]!!.substringAfterLast("/").toLong()
      startBackfillAction.start(id, StartBackfillRequest())
    }

    val runners = leaseHunter.hunt()
    assertThat(runners).hasSize(1)
    val runner = runners.single()
    assertThat(runner.backfillName).isEqualTo("ChickenSandwich")
  }

  @Test
  fun activeLeaseNotStolen() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("ChickenSandwich", listOf(), null, null, false)),
          ServiceType.SQUARE_DC))
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create("deep-fryer",
          CreateBackfillRequest("ChickenSandwich"))

      val id = response.headers["Location"]!!.substringAfterLast("/").toLong()
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
