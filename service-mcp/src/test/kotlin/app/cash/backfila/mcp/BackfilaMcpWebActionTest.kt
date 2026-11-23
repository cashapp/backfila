package app.cash.backfila.mcp

import com.google.inject.Module
import com.google.inject.util.Modules
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import misk.inject.KAbstractModule
import misk.mcp.McpToolModule
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
  val module: Module = Modules.combine(
    BackfilaMcpTestingModule(),
    object : KAbstractModule() {
      override fun configure() {
        install(McpToolModule.create<BackfilaMcp, WhoAmITool>())
      }
    },
  )

  @Inject lateinit var jettyService: JettyService

  @Test
  fun `server instructions point agents at the dashboard`(): Unit = runBlocking {
    val instructions = jettyService.mcpClient().serverInstructions

    assertThat(instructions).contains("https://backfila.example.com/")
  }

  @Test
  fun `tools see the authenticated caller`(): Unit = runBlocking {
    val result = jettyService.mcpClient(user = "molly").callTool(name = "who_am_i", arguments = mapOf())

    val output = Json.decodeFromJsonElement(WhoAmIOutput.serializer(), result.structuredContent!!)
    assertThat(output.user).isEqualTo("molly")
  }

  @ParameterizedTest
  @ValueSource(strings = ["{\"not\":\"json-rpc\"", "{\"not\":\"json-rpc\"}", ""])
  fun `malformed messages are rejected as bad requests`(body: String) {
    jettyService.postMcp(body, user = "molly").use { response ->
      assertThat(response.code).isEqualTo(400)
    }
  }
}
