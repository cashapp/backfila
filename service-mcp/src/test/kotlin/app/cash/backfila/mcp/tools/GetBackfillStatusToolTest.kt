package app.cash.backfila.mcp.tools

import app.cash.backfila.mcp.BackfilaMcpTestingModule
import app.cash.backfila.mcp.callStructured
import app.cash.backfila.mcp.createRun
import app.cash.backfila.mcp.logEvent
import app.cash.backfila.mcp.mcpClient
import app.cash.backfila.mcp.registerBackfill
import app.cash.backfila.mcp.registerService
import app.cash.backfila.protos.service.Parameter
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import com.google.inject.Module
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jakarta.inject.Inject
import java.time.Instant
import kotlinx.coroutines.runBlocking
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class GetBackfillStatusToolTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule()

  @Inject lateinit var jettyService: JettyService

  @Inject lateinit var queryFactory: Query.Factory

  @Inject @BackfilaDb
  lateinit var transacter: Transacter

  @Test
  fun `only id is required, and nullable output fields are optional`(): Unit = runBlocking {
    val tool = jettyService.mcpClient().listTools().tools.single { it.name == "get_backfill_status" }

    assertThat(tool.annotations?.readOnlyHint).isTrue()
    assertThat(tool.inputSchema.required).containsExactly("id")
    assertThat(tool.outputSchema!!.required)
      .contains("id", "state", "progress")
      .doesNotContain(
        "deleted_at",
        "unit",
        "backoff_schedule",
        "partitions",
        "event_logs",
        "next_event_log_pagination_token",
        "backfila_url",
      )
  }

  @Test
  fun `returns the run's configuration, partitions, and event logs`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer", "staging")
    val refillOil = transacter.registerBackfill(fryer, "RefillOil", parameters = listOf(Parameter("brand", null, true)))
    val runId = transacter.createRun(
      fryer,
      refillOil,
      state = BackfillState.RUNNING,
      createdByUser = "molly",
      dryRun = false,
      parameters = mapOf("brand" to "Crisco"),
    )
    transacter.transaction { session ->
      session.load(runId).partitions(session, queryFactory).single().apply {
        precomputing_done = true
        computed_matching_record_count = 1_000
        backfilled_matching_record_count = 250
        matching_records_per_minute = 50
      }
    }
    transacter.logEvent(runId, DbEventLog.Type.STATE_CHANGE, "backfill started", user = "molly")
    transacter.logEvent(runId, DbEventLog.Type.ERROR, "batch failed", extraData = "java.io.IOException: fryer offline")

    val status = getBackfillStatus(runId, includePartitions = true)

    assertThat(status.id).isEqualTo(runId.id)
    assertThat(status.service).isEqualTo("deep-fryer")
    assertThat(status.variant).isEqualTo("staging")
    assertThat(status.backfill_name).isEqualTo("RefillOil")
    assertThat(status.state).isEqualTo(RunState.RUNNING)
    assertThat(status.dry_run).isFalse()
    assertThat(status.parameters).isEqualTo(mapOf("brand" to "Crisco"))
    assertThat(status.created_by_user).isEqualTo("molly")
    assertThat(status.deleted_at).isNull()
    assertThat(status.progress).isEqualTo(
      GetBackfillStatusOutput.Progress(
        partition_count = 1,
        complete_partition_count = 0,
        precomputing_done = true,
        computed_matching_record_count = 1_000,
        backfilled_matching_record_count = 250,
        matching_records_per_minute = 50,
      ),
    )
    assertThat(status.partitions).containsExactly(
      GetBackfillStatusOutput.Partition(
        name = "-80",
        state = RunState.RUNNING,
        precomputing_done = true,
        computed_matching_record_count = 1_000,
        backfilled_matching_record_count = 250,
        matching_records_per_minute = 50,
      ),
    )
    val eventLogs = status.event_logs!!
    assertThat(eventLogs.map { it.type to it.message }).containsExactly(
      GetBackfillStatusOutput.EventLogType.ERROR to "batch failed",
      GetBackfillStatusOutput.EventLogType.STATE_CHANGE to "backfill started",
    )
    assertThat(eventLogs.first().extra_data).isEqualTo("java.io.IOException: fryer offline")
    assertThat(eventLogs.last().user).isEqualTo("molly")
    assertThat(status.next_event_log_pagination_token).isNull()
    assertThat(status.backfila_url).isEqualTo("https://backfila.example.com/backfills/${runId.id}")
  }

  @Test
  fun `reports when a run was deleted`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val runId = transacter.createRun(fryer, transacter.registerBackfill(fryer, "RefillOil"))
    val deletedAt = Instant.parse("2026-01-02T03:04:05Z")
    transacter.transaction { session -> session.load(runId).deleted_at = deletedAt }

    assertThat(getBackfillStatus(runId).deleted_at).isEqualTo(deletedAt.toString())
  }

  @Test
  fun `truncates long extra data`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val runId = transacter.createRun(fryer, transacter.registerBackfill(fryer, "RefillOil"))
    transacter.logEvent(runId, DbEventLog.Type.ERROR, "batch failed", extraData = "x".repeat(MAX_EXTRA_DATA_LENGTH + 1))

    val extraData = getBackfillStatus(runId).event_logs!!.single().extra_data!!

    assertThat(extraData).isEqualTo("x".repeat(MAX_EXTRA_DATA_LENGTH) + TRUNCATION_MARKER)
  }

  @Test
  fun `pages through older event logs`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val runId = transacter.createRun(fryer, transacter.registerBackfill(fryer, "RefillOil"))
    (1..12).forEach { transacter.logEvent(runId, DbEventLog.Type.CONFIG_CHANGE, "change $it") }

    val first = getBackfillStatus(runId)
    val second = getBackfillStatus(runId, eventLogPaginationToken = first.next_event_log_pagination_token)

    assertThat(first.event_logs).hasSize(10)
    assertThat(second.next_event_log_pagination_token).isNull()
    assertThat((first.event_logs!! + second.event_logs!!).map { it.message })
      .containsExactlyElementsOf((12 downTo 1).map { "change $it" })
  }

  @Test
  fun `leaves out partitions by default and event logs on request`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val runId = transacter.createRun(fryer, transacter.registerBackfill(fryer, "RefillOil"))
    (1..12).forEach { transacter.logEvent(runId, DbEventLog.Type.CONFIG_CHANGE, "change $it") }

    val byDefault = getBackfillStatus(runId)
    val withoutEventLogs = getBackfillStatus(runId, includeEventLogs = false)

    assertThat(byDefault.partitions).isNull()
    assertThat(byDefault.progress.partition_count).isEqualTo(1)
    assertThat(byDefault.event_logs).hasSize(10)
    assertThat(withoutEventLogs.event_logs).isNull()
    assertThat(withoutEventLogs.next_event_log_pagination_token).isNull()
  }

  @Test
  fun `invalid pagination tokens are tool errors`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val runId = transacter.createRun(fryer, transacter.registerBackfill(fryer, "RefillOil"))

    val result = jettyService.mcpClient().callTool(
      name = "get_backfill_status",
      arguments = mapOf("id" to runId.id, "event_log_pagination_token" to "next"),
    )

    assertThat(result.isError).isTrue()
    assertThat((result.content.single() as TextContent).text).contains("event_log_pagination_token")
  }

  @Test
  fun `unknown runs are tool errors that point at list_backfill_runs`(): Unit = runBlocking {
    val result = jettyService.mcpClient().callTool(name = "get_backfill_status", arguments = mapOf("id" to 404))

    assertThat(result.isError).isTrue()
    assertThat((result.content.single() as TextContent).text)
      .contains("backfill 404 doesn't exist", "list_backfill_runs")
  }

  @Test
  fun `every event log type has a counterpart`() {
    assertThat(GetBackfillStatusOutput.EventLogType.entries.map { it.name })
      .containsExactlyElementsOf(DbEventLog.Type.entries.map { it.name })
  }

  private suspend fun getBackfillStatus(
    runId: Id<DbBackfillRun>,
    includePartitions: Boolean = false,
    includeEventLogs: Boolean = true,
    eventLogPaginationToken: String? = null,
  ): GetBackfillStatusOutput = jettyService.mcpClient().callStructured(
    "get_backfill_status",
    mapOf(
      "id" to runId.id,
      "include_partitions" to includePartitions,
      "include_event_logs" to includeEventLogs,
      "event_log_pagination_token" to eventLogPaginationToken,
    ),
  )
}
