package app.cash.backfila.mcp

import com.google.inject.Module
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class BackfilaMcpModuleTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule()

  @Inject lateinit var jettyService: JettyService

  @Test
  fun `read-only servers only advertise read-only tools`(): Unit = runBlocking {
    val tools = jettyService.mcpClient().listTools().tools

    assertThat(tools).hasSize(backfilaMcpTools.count { it.readOnly })
    assertThat(tools).allSatisfy { assertThat(it.annotations?.readOnlyHint).isTrue() }
  }
}
