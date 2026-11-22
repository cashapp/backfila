package app.cash.backfila.mcp

import com.google.inject.Module
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@MiskTest(startService = true)
class BackfilaMcpWebActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule()

  @Inject lateinit var jettyService: JettyService

  @Test
  fun `server instructions point agents at the dashboard`(): Unit = runBlocking {
    val instructions = jettyService.mcpClient().serverInstructions

    assertThat(instructions).contains("https://backfila.example.com/")
  }

  @ParameterizedTest
  @ValueSource(strings = ["{\"not\":\"json-rpc\"", "{\"not\":\"json-rpc\"}", ""])
  fun `malformed messages are rejected as bad requests`(body: String) {
    jettyService.postMcp(body, user = "molly").use { response ->
      assertThat(response.code).isEqualTo(400)
    }
  }
}
