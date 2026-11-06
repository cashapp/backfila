package app.cash.backfila.mcp

import app.cash.backfila.client.Connectors
import app.cash.backfila.service.persistence.DbService
import io.modelcontextprotocol.kotlin.sdk.client.Client
import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.mcp.testing.asMcpStreamableHttpClient
import misk.security.authz.FakeCallerAuthenticator
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient

/** Connects an MCP client to this test's server, authenticated as [user]. */
internal suspend fun JettyService.mcpClient(user: String = "molly"): Client {
  val httpClient = OkHttpClient.Builder()
    .addInterceptor { chain ->
      chain.proceed(
        chain.request().newBuilder()
          .header(FakeCallerAuthenticator.USER_HEADER, user)
          .build(),
      )
    }
    .build()
  return httpClient.asMcpStreamableHttpClient(httpServerUrl, "/mcp")
}

internal fun Transacter.registerService(name: String, variant: String = "default"): Id<DbService> =
  transaction { session ->
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
