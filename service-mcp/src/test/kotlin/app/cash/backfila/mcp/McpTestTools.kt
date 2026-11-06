package app.cash.backfila.mcp

import app.cash.backfila.client.Connectors
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.service.Parameter
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.DbRunPartition
import app.cash.backfila.service.persistence.DbService
import io.modelcontextprotocol.kotlin.sdk.client.Client
import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.mcp.decode
import misk.mcp.testing.asMcpStreamableHttpClient
import misk.security.authz.FakeCallerAuthenticator
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat

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

/**
 * Calls [name] and decodes its structured output, failing if the tool returned an error. Decodes with
 * misk-mcp's JSON settings, which omit null fields.
 */
internal suspend inline fun <reified T : Any> Client.callStructured(
  name: String,
  arguments: Map<String, Any?> = mapOf(),
): T {
  val result = callTool(name = name, arguments = arguments)
  assertThat(result.isError).describedAs(result.content.toString()).isNotEqualTo(true)
  return result.structuredContent!!.decode()
}

internal fun Transacter.registerBackfill(
  serviceId: Id<DbService>,
  name: String,
  parameters: List<Parameter> = listOf(),
): Id<DbRegisteredBackfill> = transaction { session ->
  val backfill = DbRegisteredBackfill(
    service_id = serviceId,
    name = name,
    parameters = parameters,
    type_provided = null,
    type_consumed = null,
    requires_approval = false,
    delete_by = null,
  )
  val id = session.save(backfill)
  backfill.parameters.forEach { session.save(it) }
  id
}

/** Creates a run with one partition, since the dashboard's queries expect every run to have one. */
internal fun Transacter.createRun(
  serviceId: Id<DbService>,
  registeredBackfillId: Id<DbRegisteredBackfill>,
  state: BackfillState = BackfillState.PAUSED,
  createdByUser: String? = "molly",
  dryRun: Boolean = true,
  parameters: Map<String, String> = mapOf(),
): Id<DbBackfillRun> = transaction { session ->
  val runId = session.save(
    DbBackfillRun(
      service_id = serviceId,
      registered_backfill_id = registeredBackfillId,
      parameter_map = parameters.mapValues { (_, v) -> v.encodeUtf8() },
      state = state,
      created_by_user = createdByUser,
      scan_size = 10_000,
      batch_size = 100,
      num_threads = 1,
      backoff_schedule = null,
      dry_run = dryRun,
      extra_sleep_ms = 0,
    ),
  )
  session.save(
    DbRunPartition(
      backfill_run_id = runId,
      partition_name = "-80",
      backfill_range = KeyRange("0".encodeUtf8(), "1000".encodeUtf8()),
      run_state = state,
      estimated_record_count = null,
    ),
  )
  runId
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
