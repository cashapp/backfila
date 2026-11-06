package app.cash.backfila.mcp.tools

import app.cash.backfila.mcp.BackfilaMcpTestingModule
import app.cash.backfila.mcp.callStructured
import app.cash.backfila.mcp.createRun
import app.cash.backfila.mcp.mcpClient
import app.cash.backfila.mcp.registerBackfill
import app.cash.backfila.mcp.registerService
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import com.google.inject.Module
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jakarta.inject.Inject
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@MiskTest(startService = true)
class ListBackfillRunsToolTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule()

  @Inject lateinit var jettyService: JettyService

  @Inject @BackfilaDb
  lateinit var transacter: Transacter

  @Test
  fun `only service is required, and nullable output fields are optional`(): Unit = runBlocking {
    val tool = jettyService.mcpClient().listTools().tools.single { it.name == "list_backfill_runs" }

    assertThat(tool.annotations?.readOnlyHint).isTrue()
    assertThat(tool.inputSchema.required).containsExactly("service")
    assertThat(tool.outputSchema!!.required).doesNotContain("next_pagination_token")
    val run = tool.outputSchema!!.properties!!["running_runs"]!!.jsonObject["items"]!!.jsonObject
    assertThat(run["required"]!!.jsonArray.map { it.jsonPrimitive.content })
      .contains("id", "state")
      .doesNotContain("created_by_user", "backfila_url")
  }

  @Test
  fun `splits running runs from the rest and links each to the dashboard`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val refillOil = transacter.registerBackfill(fryer, "RefillOil")
    val paused = transacter.createRun(fryer, refillOil, BackfillState.PAUSED, createdByUser = "molly")
    val running = transacter.createRun(fryer, refillOil, BackfillState.RUNNING, createdByUser = null, dryRun = false)

    val output = listBackfillRuns(mapOf("service" to "deep-fryer"))

    assertThat(output.running_runs.map { it.id }).containsExactly(running.id)
    assertThat(output.other_runs.map { it.id }).containsExactly(paused.id)
    val runningRun = output.running_runs.single()
    assertThat(runningRun.backfill_name).isEqualTo("RefillOil")
    assertThat(runningRun.state).isEqualTo(RunState.RUNNING)
    assertThat(runningRun.dry_run).isFalse()
    assertThat(runningRun.created_by_user).isNull()
    assertThat(runningRun.backfila_url).isEqualTo("https://backfila.example.com/backfills/${running.id}")
    assertThat(output.other_runs.single().created_by_user).isEqualTo("molly")
    assertThat(output.next_pagination_token).isNull()
  }

  @Test
  fun `filters by backfill name and creator`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val refillOil = transacter.registerBackfill(fryer, "RefillOil")
    val drainOil = transacter.registerBackfill(fryer, "DrainOil")
    val mollysRefill = transacter.createRun(fryer, refillOil, createdByUser = "molly")
    transacter.createRun(fryer, refillOil, createdByUser = "kim")
    transacter.createRun(fryer, drainOil, createdByUser = "molly")

    val output = listBackfillRuns(
      mapOf("service" to "deep-fryer", "backfill_name" to "Refill", "created_by_user" to "mol"),
    )

    assertThat(output.other_runs.map { it.id }).containsExactly(mollysRefill.id)
  }

  @Test
  fun `hides deleted runs unless asked`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val refillOil = transacter.registerBackfill(fryer, "RefillOil")
    val kept = transacter.createRun(fryer, refillOil)
    val deleted = transacter.createRun(fryer, refillOil)
    transacter.transaction { session -> session.load(deleted).deleted_at = Instant.now() }

    val hidden = listBackfillRuns(mapOf("service" to "deep-fryer"))
    val shown = listBackfillRuns(mapOf("service" to "deep-fryer", "show_deleted" to true))

    assertThat(hidden.other_runs.map { it.id }).containsExactly(kept.id)
    assertThat(shown.other_runs.map { it.id }).containsExactly(deleted.id, kept.id)
  }

  @Test
  fun `pages through runs that aren't running`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val refillOil = transacter.registerBackfill(fryer, "RefillOil")
    val runs = (1..25).map { transacter.createRun(fryer, refillOil).id }

    val first = listBackfillRuns(mapOf("service" to "deep-fryer"))
    val second = listBackfillRuns(mapOf("service" to "deep-fryer", "pagination_token" to first.next_pagination_token))

    assertThat(first.other_runs).hasSize(20)
    assertThat(second.next_pagination_token).isNull()
    assertThat((first.other_runs + second.other_runs).map { it.id }).containsExactlyElementsOf(runs.reversed())
  }

  @Test
  fun `a blank pagination token gets the first page`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val run = transacter.createRun(fryer, transacter.registerBackfill(fryer, "RefillOil"))

    val output = listBackfillRuns(mapOf("service" to "deep-fryer", "pagination_token" to ""))

    assertThat(output.other_runs.map { it.id }).containsExactly(run.id)
  }

  @ParameterizedTest
  @ValueSource(strings = ["not-a-token", "12abc"])
  fun `invalid pagination tokens are tool errors`(token: String): Unit = runBlocking {
    transacter.registerService("deep-fryer")

    val result = jettyService.mcpClient().callTool(
      name = "list_backfill_runs",
      arguments = mapOf("service" to "deep-fryer", "pagination_token" to token),
    )

    assertThat(result.isError).isTrue()
    assertThat((result.content.single() as TextContent).text).contains("pagination_token")
  }

  @Test
  fun `unknown services are tool errors that point at list_services`(): Unit = runBlocking {
    val result = jettyService.mcpClient().callTool(name = "list_backfill_runs", arguments = mapOf("service" to "nope"))

    assertThat(result.isError).isTrue()
    assertThat((result.content.single() as TextContent).text).contains("doesn't exist", "list_services")
  }

  private suspend fun listBackfillRuns(arguments: Map<String, Any?>): ListBackfillRunsOutput =
    jettyService.mcpClient().callStructured("list_backfill_runs", arguments)
}
