package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetServiceFlavorsAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import com.google.inject.Module
import javax.inject.Inject
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class GetServiceFlavorsActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var getServiceFlavorsAction: GetServiceFlavorsAction

  @Inject
  lateinit var scope: ActionScope

  @Inject
  lateinit var createBackfillAction: CreateBackfillAction

  @Inject
  lateinit var startBackfillAction: StartBackfillAction

  @Test
  fun nonExistentService() {
    scope.fakeCaller(user = "molly") {
      Assertions.assertThat(getServiceFlavorsAction.flavors("non-existent-service").flavors).isEmpty()
    }
  }

  @Test
  fun oneService() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .flavor("deep-fried")
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null, "String",
                false, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null, "String",
                false, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        "deep-fried",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
      val id = response.backfill_run_id
      startBackfillAction.start(id, StartBackfillRequest())
    }

    scope.fakeCaller(user = "molly") {
      Assertions.assertThat(getServiceFlavorsAction.flavors("deep-fryer").flavors).containsOnly(
        GetServiceFlavorsAction.UiFlavor("deep-fried", 1),
        GetServiceFlavorsAction.UiFlavor("flavorless", 0),
      )
    }
  }

  @Test
  fun twoServices() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .flavor("deep-fried")
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData.Builder()
                .name("ChickenSandwich")
                .build(),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        "deep-fried",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
      val id = response.backfill_run_id
      startBackfillAction.start(id, StartBackfillRequest())
    }
    scope.fakeCaller(service = "oven") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .flavor("under-baked")
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }

    scope.fakeCaller(user = "molly") {
      Assertions.assertThat(getServiceFlavorsAction.flavors("deep-fryer").flavors).containsOnly(
        GetServiceFlavorsAction.UiFlavor("deep-fried", 1),
      )
      Assertions.assertThat(getServiceFlavorsAction.flavors("oven").flavors).containsOnly(
        GetServiceFlavorsAction.UiFlavor("under-baked", 0),
      )
    }
  }
}
