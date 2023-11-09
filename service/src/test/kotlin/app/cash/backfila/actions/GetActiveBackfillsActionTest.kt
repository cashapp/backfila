package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetActiveBackfillsAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.dashboard.StopBackfillRequest
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfillState
import com.google.inject.Module
import javax.inject.Inject
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class GetActiveBackfillsActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var getActiveBackfillsAction: GetActiveBackfillsAction

  @Inject
  lateinit var stopBackfillAction: StopBackfillAction

  @Inject
  lateinit var scope: ActionScope

  @Inject
  lateinit var createBackfillAction: CreateBackfillAction

  @Inject
  lateinit var startBackfillAction: StartBackfillAction

  @Inject
  private lateinit var fakeClock: FakeClock

  private val deepFryerService = "deep-fryer"
  private val chickenSandwichBackfillName = "ChickenSandwich"
  private val baconBurgerBackfillName = "BaconBurger"

  @Test
  fun shouldReturnEmptyListWhenThereAreNoActiveBackfills() {
    createdBackfillsForService(
      deepFryerService, listOf(chickenSandwichBackfillName, baconBurgerBackfillName),
    )

    scope.fakeCaller(user = "molly") {
      assertThat(getActiveBackfillsAction.currentlyRunningBackfills().currentlyRunningBackfillSummaries).isEmpty()
    }
  }

  @Test
  fun shouldNotIncludeStoppedBackfills() {
    createdBackfillsForService(
      deepFryerService, listOf(chickenSandwichBackfillName, baconBurgerBackfillName),
    )

    val baconBurgerBackfillId =
      createBackfillRun(deepFryerService, baconBurgerBackfillName, false)
    scope.fakeCaller(user = "molly") {
      startBackfillAction.start(baconBurgerBackfillId, StartBackfillRequest())
    }

    scope.fakeCaller(user = "molly") {
      stopBackfillAction.stop(baconBurgerBackfillId, StopBackfillRequest())
    }

    scope.fakeCaller(user = "molly") {
      assertThat(getActiveBackfillsAction.currentlyRunningBackfills().currentlyRunningBackfillSummaries).isEmpty()
    }
  }

  @Test
  fun shouldReturnRunningBackfills() {
    val currentTime = fakeClock.instant()
    val creationTime = currentTime.minus(2, java.time.temporal.ChronoUnit.HOURS)
    fakeClock.setNow(creationTime)

    createdBackfillsForService(deepFryerService, listOf(chickenSandwichBackfillName, baconBurgerBackfillName))

    val freezerService = "freezer"
    val rawChickenBackfillName = "RawChicken"
    createdBackfillsForService(freezerService, listOf(rawChickenBackfillName, "RawBacon"))

    val chickenSandwichBackfillId =
      createBackfillRun(deepFryerService, chickenSandwichBackfillName, false)
    val rawChickenBackfillId = createBackfillRun(freezerService, rawChickenBackfillName, true)

    val backfillStartTime = currentTime.minus(1, java.time.temporal.ChronoUnit.HOURS)
    fakeClock.setNow(backfillStartTime)
    scope.fakeCaller(user = "molly") {
      startBackfillAction.start(rawChickenBackfillId, StartBackfillRequest())
    }

    scope.fakeCaller(user = "molly") {
      startBackfillAction.start(chickenSandwichBackfillId, StartBackfillRequest())
    }
    scope.fakeCaller(user = "molly") {
      val expectedChickenSandwichBackfill =
        GetActiveBackfillsAction.CurrentlyRunningBackfillSummary(
          chickenSandwichBackfillId.toString(), chickenSandwichBackfillName, deepFryerService, BackfillState.RUNNING, false, creationTime,
          "molly", backfillStartTime,
        )
      val expectedRawChickenBackfill =
        GetActiveBackfillsAction.CurrentlyRunningBackfillSummary(
          rawChickenBackfillId.toString(), rawChickenBackfillName, freezerService, BackfillState.RUNNING, true, creationTime,
          "molly", backfillStartTime,
        )
      val currentlyRunningBackfills =
        getActiveBackfillsAction.currentlyRunningBackfills().currentlyRunningBackfillSummaries
      assertThat(currentlyRunningBackfills)
        .containsExactly(expectedChickenSandwichBackfill, expectedRawChickenBackfill)
    }
  }

  private fun createBackfillRun(serviceName: String, backfillName: String, dryRun: Boolean): Long =
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        serviceName,
        CreateBackfillRequest.Builder()
          .backfill_name(backfillName)
          .dry_run(dryRun)
          .build(),
      )
      response.backfill_run_id
    }

  private fun createdBackfillsForService(serviceName: String, backfillNames: List<String>) {
    scope.fakeCaller(service = serviceName) {
      val backfills = backfillNames.map {
        ConfigureServiceRequest.BackfillData.Builder()
          .name(it)
          .build()
      }
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(backfills)
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
  }
}
