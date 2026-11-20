package app.cash.backfila.mcp

import com.google.inject.Module
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequest
import io.modelcontextprotocol.kotlin.sdk.types.InitializeRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.toJSON
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import misk.mcp.encodeToString
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    val initialize = InitializeRequest(
      InitializeRequestParams(
        protocolVersion = LATEST_PROTOCOL_VERSION,
        capabilities = ClientCapabilities(),
        clientInfo = Implementation(name = "test", version = "1.0"),
      ),
    ).toJSON()

    OkHttpClient().newCall(
      Request.Builder()
        .url(jettyService.httpServerUrl.resolve("/mcp")!!)
        .header("Accept", "application/json, text/event-stream")
        .post(initialize.encodeToString().toRequestBody("application/json".toMediaType()))
        .build(),
    ).execute().use { response ->
      assertThat(response.code).isEqualTo(401)
    }
  }

  @Test
  fun `authenticated callers can still connect`(): Unit = runBlocking {
    assertThat(jettyService.mcpClient(user = "molly").serverInstructions).isNotBlank()
  }
}
