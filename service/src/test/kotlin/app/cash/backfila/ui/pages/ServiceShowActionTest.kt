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
    // After sanitization, "ki/ki" is stored as "ki-ki", so the path is clean
    val path = ServiceShowAction.path("ki-ki", null)
    assertThat(path).doesNotContain("/ki/ki/")
    assertThat(path).isEqualTo("/services/ki-ki/")
  }

  @Test
  fun `service with slash in name should be reachable after sanitization`() {
    // Register with caller identity "ki/ki" — the slash gets replaced with dash
    scope.fakeCaller(service = "ki/ki") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }

    // The service is stored as "ki-ki" and is reachable with that sanitized name
    scope.fakeCaller(user = "molly") {
      val response = getBackfillRunsAction.backfillRuns(service = "ki-ki", variant = "default")
      assertThat(response).isNotNull()
    }
  }
}
