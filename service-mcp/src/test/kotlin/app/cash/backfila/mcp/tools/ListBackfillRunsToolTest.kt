package app.cash.backfila.mcp.tools

import app.cash.backfila.mcp.BackfilaMcpTestingModule
import app.cash.backfila.mcp.callStructured
import app.cash.backfila.mcp.createRun
import app.cash.backfila.mcp.mcpClient
import app.cash.backfila.mcp.registerBackfill
import app.cash.backfila.mcp.registerService
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import com.google.inject.Module
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jakarta.inject.Inject
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class ListBackfillRunsToolTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule()

  @Inject lateinit var jettyService: JettyService

  @Inject @BackfilaDb
  lateinit var transacter: Transacter

  @Inject lateinit var clock: Clock

  @Test
  fun `no filter is required, and nullable output fields are optional`(): Unit = runBlocking {
    val tool = jettyService.mcpClient().listTools().tools.single { it.name == "list_backfill_runs" }

    assertThat(tool.annotations?.readOnlyHint).isTrue()
    assertThat(tool.inputSchema.required.orEmpty()).isEmpty()
    assertThat(tool.outputSchema!!.required).doesNotContain("next_pagination_token")
    val run = tool.outputSchema!!.properties!!["runs"]!!.jsonObject["items"]!!.jsonObject
    assertThat(run["required"]!!.jsonArray.map { it.jsonPrimitive.content })
      .contains("id", "service", "variant", "state")
      .doesNotContain("created_by_user", "percent_complete", "backfila_url")
  }

  @Test
  fun `finds runs in every service, newest first, with progress and a dashboard link`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val grill = transacter.registerService("grill", variant = "patio")
    val older = transacter.createRun(fryer, transacter.registerBackfill(fryer, "RefillOil"), createdByUser = null)
    val newer = transacter.createRun(grill, transacter.registerBackfill(grill, "CleanGrates"), BackfillState.RUNNING)
    transacter.setProgress(newer, computed = 200, backfilled = 50)

    val runs = listBackfillRuns(mapOf()).runs

    assertThat(runs.map { it.id }).containsExactly(newer.id, older.id)
    val run = runs.first()
    assertThat(run.service).isEqualTo("grill")
    assertThat(run.variant).isEqualTo("patio")
    assertThat(run.backfill_name).isEqualTo("CleanGrates")
    assertThat(run.state).isEqualTo(RunState.RUNNING)
    assertThat(run.created_by_user).isEqualTo("molly")
    assertThat(run.precomputing_done).isTrue()
    assertThat(run.computed_matching_record_count).isEqualTo(200)
    assertThat(run.backfilled_matching_record_count).isEqualTo(50)
    assertThat(run.percent_complete).isEqualTo(25.0)
    assertThat(run.backfila_url).isEqualTo("https://backfila.example.com/backfills/${newer.id}")
    assertThat(runs.last().created_by_user).isNull()
    assertThat(runs.last().percent_complete).isNull()
  }

  @Test
  fun `a service without a variant includes every variant`(): Unit = runBlocking {
    val usa = transacter.registerService("fryer", variant = "usa")
    val canada = transacter.registerService("fryer", variant = "canada")
    val usaRun = transacter.createRun(usa, transacter.registerBackfill(usa, "RefillOil"))
    val canadaRun = transacter.createRun(canada, transacter.registerBackfill(canada, "RefillOil"))
    transacter.registerService("grill").let { transacter.createRun(it, transacter.registerBackfill(it, "CleanGrates")) }

    assertThat(listBackfillRuns(mapOf("service" to "fryer")).runs.map { it.id }).containsExactly(canadaRun.id, usaRun.id)
    assertThat(listBackfillRuns(mapOf("service" to "fryer", "variant" to "usa")).runs.map { it.id }).containsExactly(usaRun.id)
  }

  @Test
  fun `matches backfill names ignoring case`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val refill = transacter.createRun(fryer, transacter.registerBackfill(fryer, "RefillOil"))
    transacter.createRun(fryer, transacter.registerBackfill(fryer, "DrainOil"))

    assertThat(listBackfillRuns(mapOf("backfill_name" to "refill")).runs.map { it.id }).containsExactly(refill.id)
    assertThat(listBackfillRuns(mapOf("backfill_name" to "nothing-matches")).runs).isEmpty()
  }

  @Test
  fun `filters by creator, including the caller`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val refillOil = transacter.registerBackfill(fryer, "RefillOil")
    val mollys = transacter.createRun(fryer, refillOil, createdByUser = "molly")
    val kims = transacter.createRun(fryer, refillOil, createdByUser = "kim")

    assertThat(listBackfillRuns(mapOf("created_by_user" to "kim")).runs.map { it.id }).containsExactly(kims.id)
    assertThat(listBackfillRuns(mapOf("created_by_me" to true)).runs.map { it.id }).containsExactly(mollys.id)
    val asKim = jettyService.mcpClient(user = "kim")
      .callStructured<ListBackfillRunsOutput>("list_backfill_runs", mapOf("created_by_me" to true))
    assertThat(asKim.runs.map { it.id }).containsExactly(kims.id)
  }

  @Test
  fun `filters by state and creation time`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val refillOil = transacter.registerBackfill(fryer, "RefillOil")
    val running = transacter.createRun(fryer, refillOil, BackfillState.RUNNING)
    val paused = transacter.createRun(fryer, refillOil, BackfillState.PAUSED)
    transacter.createRun(fryer, refillOil, BackfillState.COMPLETE)
    val yesterday = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(1)
    val tomorrow = clock.instant().plus(1, ChronoUnit.DAYS)

    assertThat(listBackfillRuns(mapOf("states" to listOf("RUNNING", "PAUSED"))).runs.map { it.id })
      .containsExactly(paused.id, running.id)
    assertThat(listBackfillRuns(mapOf("created_after" to yesterday.toString())).runs).hasSize(3)
    assertThat(listBackfillRuns(mapOf("created_after" to tomorrow.toString())).runs).isEmpty()
  }

  @Test
  fun `hides deleted runs unless asked`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val refillOil = transacter.registerBackfill(fryer, "RefillOil")
    val kept = transacter.createRun(fryer, refillOil)
    val deleted = transacter.createRun(fryer, refillOil)
    transacter.transaction { session -> session.load(deleted).deleted_at = Instant.now() }

    assertThat(listBackfillRuns(mapOf()).runs.map { it.id }).containsExactly(kept.id)
    assertThat(listBackfillRuns(mapOf("show_deleted" to true)).runs.map { it.id }).containsExactly(deleted.id, kept.id)
  }

  @Test
  fun `pages through runs`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val refillOil = transacter.registerBackfill(fryer, "RefillOil")
    val runs = (1..25).map { transacter.createRun(fryer, refillOil).id }

    val first = listBackfillRuns(mapOf())
    val second = listBackfillRuns(mapOf("pagination_token" to first.next_pagination_token))

    assertThat(first.runs).hasSize(RUNS_PAGE_SIZE)
    assertThat(second.next_pagination_token).isNull()
    assertThat((first.runs + second.runs).map { it.id }).containsExactlyElementsOf(runs.reversed())
  }

  @Test
  fun `blank filters and tokens are ignored`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val run = transacter.createRun(fryer, transacter.registerBackfill(fryer, "RefillOil"))

    val output = listBackfillRuns(
      mapOf("service" to "", "variant" to "", "backfill_name" to "", "created_by_user" to "", "created_after" to "", "pagination_token" to ""),
    )

    assertThat(output.runs.map { it.id }).containsExactly(run.id)
  }

  @Test
  fun `bad arguments are tool errors that say what to do`(): Unit = runBlocking {
    transacter.registerService("deep-fryer")
    val client = jettyService.mcpClient()

    mapOf(
      mapOf("pagination_token" to "not-a-token") to "returned by a previous call",
      mapOf("created_after" to "last tuesday") to "ISO-8601",
      mapOf("created_by_me" to true, "created_by_user" to "kim") to "not both",
      mapOf("service" to "nope") to "list_services",
    ).forEach { (arguments, hint) ->
      val result = client.callTool(name = "list_backfill_runs", arguments = arguments)

      assertThat(result.isError).describedAs(arguments.toString()).isTrue()
      assertThat((result.content.single() as TextContent).text).contains(hint)
    }
  }

  private suspend fun listBackfillRuns(arguments: Map<String, Any?>): ListBackfillRunsOutput =
    jettyService.mcpClient().callStructured("list_backfill_runs", arguments)

  private fun Transacter.setProgress(run: Id<DbBackfillRun>, computed: Long, backfilled: Long) = transaction { session ->
    session.hibernateSession.createQuery(
      """
        update DbRunPartition
        set precomputing_done = true, computed_matching_record_count = :computed, backfilled_matching_record_count = :backfilled
        where backfill_run_id = :run
      """,
    ).setParameter("computed", computed).setParameter("backfilled", backfilled).setParameter("run", run).executeUpdate()
  }
}
