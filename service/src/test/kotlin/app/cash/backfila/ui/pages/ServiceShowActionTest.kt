package app.cash.backfila.ui.pages

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.GetBackfillRunsAction
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
class ServiceShowActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction

  @Inject lateinit var getBackfillRunsAction: GetBackfillRunsAction

  @Inject lateinit var scope: ActionScope

  @Test
  fun `service name with slash should not produce extra path segments`() {
    val path = ServiceShowAction.path("ki/ki", null)
    // The slash should be encoded, not treated as a path separator
    assertThat(path).doesNotContain("/ki/ki/")
    assertThat(path).contains("ki%2Fki")
  }

  @Test
  fun `service with slash in name should be reachable`() {
    scope.fakeCaller(service = "ki/ki") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }

    // With URL encoding, the router would correctly decode %2F back to /
    // and extract service="ki/ki", variant="default". The service should be reachable.
    scope.fakeCaller(user = "molly") {
      val response = getBackfillRunsAction.backfillRuns(service = "ki/ki", variant = "default")
      assertThat(response).isNotNull()
    }
  }
}
