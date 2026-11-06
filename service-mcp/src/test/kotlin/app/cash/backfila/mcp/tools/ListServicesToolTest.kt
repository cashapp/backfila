package app.cash.backfila.mcp.tools

import app.cash.backfila.client.Connectors
import app.cash.backfila.mcp.BackfilaMcpTestingModule
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbService
import com.google.inject.Module
import io.modelcontextprotocol.kotlin.sdk.client.Client
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import misk.hibernate.Transacter
import misk.mcp.testing.asMcpStreamableHttpClient
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
class ListServicesToolTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule()

  @Inject lateinit var jettyService: JettyService

  @Inject @BackfilaDb
  lateinit var transacter: Transacter

  @Test
  fun `list_services is advertised as a read-only tool`(): Unit = runBlocking {
    val client = authenticatedClient()

    val tool = client.listTools().tools.single { it.name == "list_services" }

    assertThat(tool.annotations?.readOnlyHint).isTrue()
    assertThat(tool.outputSchema).isNotNull()
  }

  @Test
  fun `server instructions point agents at list_services and the dashboard`(): Unit = runBlocking {
    val instructions = authenticatedClient().serverInstructions

    assertThat(instructions)
      .contains("list_services")
      .contains("https://backfila.example.com/")
  }

  @Test
  fun `list_services returns registered services and their variants`(): Unit = runBlocking {
    registerService("deep-fryer", "default")
    registerService("deep-fryer", "staging")
    registerService("grill", "default")

    val result = authenticatedClient().callTool(name = "list_services", arguments = mapOf())

    assertThat(result.isError).isNotEqualTo(true)
    val output = Json.decodeFromJsonElement(ListServicesOutput.serializer(), result.structuredContent!!)
    assertThat(output.services).containsExactly(
      ListServicesOutput.Service(name = "deep-fryer", variants = listOf("default", "staging"), running_backfills = 0),
      ListServicesOutput.Service(name = "grill", variants = listOf("default"), running_backfills = 0),
    )
  }

  @Test
  fun `unauthenticated callers are rejected`() {
    val initialize = """
      {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18",
      "capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}
    """.trimIndent()
    val request = Request.Builder()
      .url(jettyService.httpServerUrl.resolve("/mcp")!!)
      .header("Accept", "application/json, text/event-stream")
      .post(initialize.toRequestBody("application/json".toMediaType()))
      .build()

    OkHttpClient().newCall(request).execute().use { response ->
      assertThat(response.code).isEqualTo(401)
    }
  }
  private suspend fun authenticatedClient(): Client {
    val httpClient = OkHttpClient.Builder()
      .addInterceptor { chain ->
        chain.proceed(
          chain.request().newBuilder()
            .header(FakeCallerAuthenticator.USER_HEADER, "molly")
            .build(),
        )
      }
      .build()
    return httpClient.asMcpStreamableHttpClient(jettyService.httpServerUrl, "/mcp")
  }

  private fun registerService(name: String, variant: String) {
    transacter.transaction { session ->
      session.save(
        DbService(
          registry_name = name,
          connector = Connectors.ENVOY,
          connector_extra_data = null,
          slack_channel = null,
          variant = variant,
          last_registered_at = null,
        ),
      )
    }
  }
}
