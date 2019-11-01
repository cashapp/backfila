package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.GetServicesAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
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

  @Inject lateinit var configureServiceAction: ConfigureServiceAction
  @Inject lateinit var getServicesAction: GetServicesAction
  @Inject lateinit var scope: ActionScope

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
          ConfigureServiceRequest.Builder().connector_type(Connectors.ENVOY).build())
    }

    scope.fakeCaller(user = "molly") {
      assertThat(getServicesAction.services().services).containsOnly("deep-fryer")
    }
  }

  @Test
  fun twoServices() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
          ConfigureServiceRequest.Builder().connector_type(Connectors.ENVOY).build())
    }
    scope.fakeCaller(service = "freezer") {
      configureServiceAction.configureService(
          ConfigureServiceRequest.Builder().connector_type(Connectors.ENVOY).build())
    }
    scope.fakeCaller(user = "molly") {
      assertThat(getServicesAction.services().services).containsOnly("deep-fryer", "freezer")
    }
  }
}
