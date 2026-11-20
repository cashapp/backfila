package app.cash.backfila.mcp.tools

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.dashboard.UiBackfillRun
import app.cash.backfila.mcp.DashboardLinks
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.serialization.Serializable
import misk.mcp.Description

@Serializable
internal data class ListBackfillRunsInput(
  @Description("Service name, from list_services.")
  val service: String,
  @Description("Service variant, from list_services. Defaults to \"default\".")
  val variant: String = "default",
  @Description("Only include runs whose backfill name contains this text.")
  val backfill_name: String? = null,
  @Description("Only include runs whose creator's username contains this text.")
  val created_by_user: String? = null,
  @Description("Include runs that were deleted from the dashboard.")
  val show_deleted: Boolean = false,
  @Description("next_pagination_token from a previous call, to get the next page of other_runs.")
  val pagination_token: String? = null,
)

@Serializable
internal data class ListBackfillRunsOutput(
  @Description("Every running backfill run, newest first. Not paginated.")
  val running_runs: List<Run>,
  @Description("Runs that are paused, complete, or cancelled, newest first, one page at a time.")
  val other_runs: List<Run>,
  @Description("Pass as pagination_token to get the next page of other_runs. Absent on the last page.")
  val next_pagination_token: String?,
) {
  @Serializable
  data class Run(
    @Description("Backfill run ID.")
    val id: Long,
    @Description("Name of the registered backfill this run executes.")
    val backfill_name: String,
    @Description("Current state of the run.")
    val state: RunState,
    @Description("When true, the run reads records but doesn't change them.")
    val dry_run: Boolean,
    @Description("When the run was created, as an ISO-8601 timestamp.")
    val created_at: String,
    @Description("Username of whoever created the run, if known.")
    val created_by_user: String?,
    @Description("When the run's state or settings last changed, as an ISO-8601 timestamp. Batch progress doesn't update it.")
    val last_active_at: String,
    @Description("True once every partition has finished counting the records it will process.")
    val precomputing_done: Boolean,
    @Description("Records expected to match across all partitions. Partial until precomputing_done is true.")
    val computed_matching_record_count: Long,
    @Description("Matching records backfilled so far.")
    val backfilled_matching_record_count: Long,
    @Description("The run's page in Backfila, where a user can view, start, pause, or edit it. Absent if Backfila's URL isn't configured.")
    val backfila_url: String?,
  )
}

@Singleton
internal class ListBackfillRunsTool @Inject constructor(
  private val getBackfillRunsAction: GetBackfillRunsAction,
  private val dashboardLinks: DashboardLinks,
) : BackfilaMcpTool<ListBackfillRunsInput, ListBackfillRunsOutput>() {
  override val name = "list_backfill_runs"
  override val title = "List backfill runs"
  override val description =
    "Lists backfill runs for a service variant: every running run, plus a page of runs that are paused, complete, or cancelled."
  override val readOnlyHint = true
  override val openWorldHint = false

  override suspend fun call(input: ListBackfillRunsInput): ListBackfillRunsOutput {
    val paginationToken = paginationToken("pagination_token", input.pagination_token)
    val response = callDashboard(UNKNOWN_SERVICE_HINT) {
      getBackfillRunsAction.backfillRuns(
        service = input.service,
        variant = input.variant,
        pagination_token = paginationToken,
        backfill_name = input.backfill_name,
        created_by_user = input.created_by_user,
        show_deleted = input.show_deleted,
      )
    }
    return ListBackfillRunsOutput(
      running_runs = response.running_backfills.map { it.toRun() },
      other_runs = response.paused_backfills.map { it.toRun() },
      next_pagination_token = response.next_pagination_token,
    )
  }

  private fun UiBackfillRun.toRun(): ListBackfillRunsOutput.Run {
    val runId = id.toLong()
    return ListBackfillRunsOutput.Run(
      id = runId,
      backfill_name = name,
      state = state.toRunState(),
      dry_run = dry_run,
      created_at = created_at.toString(),
      created_by_user = created_by_user,
      last_active_at = last_active_at.toString(),
      precomputing_done = precomputing_done,
      computed_matching_record_count = computed_matching_record_count,
      backfilled_matching_record_count = backfilled_matching_record_count,
      backfila_url = dashboardLinks.backfillRun(runId),
    )
  }
}
