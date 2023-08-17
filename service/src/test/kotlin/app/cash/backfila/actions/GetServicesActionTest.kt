package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetServicesAction
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class GetServicesActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var getServicesAction: GetServicesAction

  @Inject
  lateinit var scope: ActionScope

  @Inject
  lateinit var createBackfillAction: CreateBackfillAction

  @Inject
  lateinit var startBackfillAction: StartBackfillAction

  @Test
  fun noServices() {
    scope.fakeCaller(user = "molly") {
      assertThat(getServicesAction.services().services).isEmpty()
    }
  }

  @Test
  fun oneService() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder().connector_type(Connectors.ENVOY).build(),
      )
    }

    scope.fakeCaller(user = "molly") {
      assertThat(getServicesAction.services().services).containsOnly(
        GetServicesAction.UiService("deep-fryer", setOf(RESERVED_VARIANT), 0),
      )
    }
  }

  @Test
  fun oneService_multipleVariants() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }

    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .connector_type(Connectors.ENVOY)
          .variant("deep-fried")
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData.Builder()
                .name("ChickenSandwich")
                .build(),
            ),
          )
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
      assertThat(getServicesAction.services().services).containsOnly(
        GetServicesAction.UiService("deep-fryer", setOf(RESERVED_VARIANT, "deep-fried"), 1),
      )
    }
  }

  @Test
  fun twoServices() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
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
    scope.fakeCaller(service = "freezer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder().connector_type(Connectors.ENVOY).build(),
      )
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        null,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
      val id = response.backfill_run_id
      startBackfillAction.start(id, StartBackfillRequest())
    }
    scope.fakeCaller(user = "molly") {
      assertThat(getServicesAction.services().services).containsOnly(
        GetServicesAction.UiService("deep-fryer", setOf(RESERVED_VARIANT), 1),
        GetServicesAction.UiService("freezer", setOf(RESERVED_VARIANT), 0),
      )
    }
  }
}
