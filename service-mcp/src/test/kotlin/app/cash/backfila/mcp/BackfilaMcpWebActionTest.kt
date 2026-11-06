package app.cash.backfila.mcp

import com.google.inject.Module
import com.google.inject.util.Modules
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import misk.inject.KAbstractModule
import misk.mcp.McpToolModule
import misk.mcp.encodeToString
import misk.security.authz.FakeCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class BackfilaMcpWebActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = Modules.combine(
    BackfilaMcpTestingModule(),
    object : KAbstractModule() {
      override fun configure() {
        install(McpToolModule.create<WhoAmITool>())
      }
    },
  )

  @Inject lateinit var jettyService: JettyService

  @Test
  fun `server instructions point agents at list_services and the dashboard`(): Unit = runBlocking {
    val instructions = jettyService.mcpClient().serverInstructions

    assertThat(instructions)
      .contains("list_services")
      .contains("https://backfila.example.com/")
  }

  @Test
  fun `tools see the authenticated caller`(): Unit = runBlocking {
    val result = jettyService.mcpClient(user = "molly").callTool(name = "who_am_i", arguments = mapOf())

    val output = Json.decodeFromJsonElement(WhoAmIOutput.serializer(), result.structuredContent!!)
    assertThat(output.user).isEqualTo("molly")
  }

  @Test
  fun `unauthenticated callers are rejected`() {
    val initialize = InitializeRequest(
      InitializeRequestParams(
        protocolVersion = LATEST_PROTOCOL_VERSION,
        capabilities = ClientCapabilities(),
        clientInfo = Implementation(name = "test", version = "1.0"),
      ),
    ).toJSON()

    post(initialize.encodeToString(), user = null).use { response ->
      assertThat(response.code).isEqualTo(401)
    }
  }

  @Test
  fun `malformed messages are rejected as bad requests`() {
    post("{\"not\":\"json-rpc\"", user = "molly").use { response ->
      assertThat(response.code).isEqualTo(400)
    }
  }

  private fun post(body: String, user: String?) = OkHttpClient().newCall(
    Request.Builder()
      .url(jettyService.httpServerUrl.resolve("/mcp")!!)
      .header("Accept", "application/json, text/event-stream")
      .apply { if (user != null) header(FakeCallerAuthenticator.USER_HEADER, user) }
      .post(body.toRequestBody("application/json".toMediaType()))
      .build(),
  ).execute()
}
