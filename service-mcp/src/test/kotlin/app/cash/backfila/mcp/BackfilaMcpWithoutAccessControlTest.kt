package app.cash.backfila.mcp

import com.google.inject.Module
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** A host that enables MCP but forgets `AccessControlModule` must not expose `/mcp` to anonymous callers. */
@MiskTest(startService = true)
class BackfilaMcpWithoutAccessControlTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule(installAccessControl = false)

  @Inject lateinit var jettyService: JettyService

  @Test
  fun `unauthenticated callers are rejected`() {
    jettyService.postMcp("{}", user = null).use { response ->
      assertThat(response.code).isEqualTo(401)
    }
  }

  @Test
  fun `authenticated callers can still connect`(): Unit = runBlocking {
    assertThat(jettyService.mcpClient(user = "molly").serverInstructions).isNotBlank()
  }
}
