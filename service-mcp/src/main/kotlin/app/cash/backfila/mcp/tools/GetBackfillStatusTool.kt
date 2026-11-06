package app.cash.backfila.mcp.tools

import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.UiPartition
import app.cash.backfila.mcp.DashboardLinks
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbEventLog
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.serialization.Serializable
import misk.mcp.Description

@Serializable
internal data class GetBackfillStatusInput(
  @Description("Backfill run ID, from list_backfill_runs.")
  val id: Long,
  @Description("Include each partition's state and progress. Off by default; progress always has the totals.")
  val include_partitions: Boolean = false,
  @Description("Include a page of recent event logs, which carry error details. On by default.")
  val include_event_logs: Boolean = true,
  @Description("next_event_log_pagination_token from a previous call, to get older event logs.")
  val event_log_pagination_token: String? = null,
)

@Serializable
internal data class GetBackfillStatusOutput(
  val id: Long,
  val service: String,
  val variant: String,
  @Description("Name of the registered backfill this run executes.")
  val backfill_name: String,
  val state: RunState,
  @Description("When true, the run reads records but doesn't change them.")
  val dry_run: Boolean,
  @Description("Parameters the run was created with, as UTF-8 strings.")
  val parameters: Map<String, String>,
  @Description("When the run was created, as an ISO-8601 timestamp.")
  val created_at: String,
  @Description("Username of whoever created the run, if known.")
  val created_by_user: String?,
  @Description("When the run was deleted from the dashboard, as an ISO-8601 timestamp. Absent if it wasn't.")
  val deleted_at: String?,
  @Description("Records scanned per batch request to the service.")
  val scan_size: Long,
  @Description("Matching records sent to the service per batch.")
  val batch_size: Long,
  @Description("Batches run concurrently per partition.")
  val num_threads: Int,
  @Description("Extra delay between batches, in milliseconds.")
  val extra_sleep_ms: Long,
  @Description("Comma-separated retry delays in milliseconds, if the run overrides the default.")
  val backoff_schedule: String?,
  @Description("What the service counts as one record, such as \"rows\", if it says.")
  val unit: String?,
  val progress: Progress,
  @Description("Each partition's state and progress. Absent unless include_partitions is true.")
  val partitions: List<Partition>?,
  @Description("Most recent event logs first, one page at a time. Absent if include_event_logs is false.")
  val event_logs: List<EventLog>?,
  @Description("Pass as event_log_pagination_token to get older event logs. Absent on the last page.")
  val next_event_log_pagination_token: String?,
  @Description("The run's page in Backfila, where a user can view, start, pause, or edit it. Absent if Backfila's URL isn't configured.")
  val backfila_url: String?,
) {
  @Serializable
  data class Progress(
    val partition_count: Int,
    @Description("Partitions that have finished backfilling.")
    val complete_partition_count: Int,
    @Description("True once every partition has finished counting the records it will process.")
    val precomputing_done: Boolean,
    @Description("Records the run expects to process. Partial until precomputing_done is true.")
    val computed_matching_record_count: Long,
    @Description("Matching records backfilled so far.")
    val backfilled_matching_record_count: Long,
    @Description("Recent backfill rate across running partitions.")
    val matching_records_per_minute: Long,
  )

  @Serializable
  data class Partition(
    val name: String,
    val state: RunState,
    @Description("True once this partition has finished counting the records it will process.")
    val precomputing_done: Boolean,
    @Description("Records this partition expects to process. Partial until precomputing_done is true.")
    val computed_matching_record_count: Long,
    @Description("Matching records this partition has backfilled so far.")
    val backfilled_matching_record_count: Long,
    @Description("Recent backfill rate, if the partition is running.")
    val matching_records_per_minute: Long?,
  )

  @Serializable
  data class EventLog(
    @Description("ISO-8601 timestamp.")
    val occurred_at: String,
    val type: EventLogType,
    @Description("Username of whoever caused the event, if a person did.")
    val user: String?,
    @Description("Partition the event applies to. Absent for events about the whole run.")
    val partition_name: String?,
    val message: String,
    @Description("More detail, such as an error's stack trace. Long values are truncated.")
    val extra_data: String?,
  )

  /** Mirrors [DbEventLog.Type] so the schema lists the valid types. */
  @Serializable
  enum class EventLogType {
    STATE_CHANGE,
    CONFIG_CHANGE,
    ERROR,
  }
}

@Singleton
internal class GetBackfillStatusTool @Inject constructor(
  private val getBackfillStatusAction: GetBackfillStatusAction,
  private val dashboardLinks: DashboardLinks,
) : BackfilaMcpTool<GetBackfillStatusInput, GetBackfillStatusOutput>() {
  override val name = "get_backfill_status"
  override val title = "Get backfill status"
  override val description =
    "Gets one backfill run's configuration, overall progress, and recent event logs. " +
      "To find out why a run failed, look at its ERROR event logs."
  override val readOnlyHint = true
  override val openWorldHint = false

  override suspend fun call(input: GetBackfillStatusInput): GetBackfillStatusOutput {
    val offset = paginationToken("event_log_pagination_token", input.event_log_pagination_token)
    val status = callDashboard("Call list_backfill_runs to find run IDs.") {
      getBackfillStatusAction.status(input.id, offset)
    }
    return GetBackfillStatusOutput(
      id = input.id,
      service = status.service_name,
      variant = status.variant,
      backfill_name = status.name,
      state = status.state.toRunState(),
      dry_run = status.dry_run,
      parameters = status.parameters.orEmpty(),
      created_at = status.created_at.toString(),
      created_by_user = status.created_by_user,
      deleted_at = status.deleted_at?.toString(),
      scan_size = status.scan_size,
      batch_size = status.batch_size,
      num_threads = status.num_threads,
      extra_sleep_ms = status.extra_sleep_ms,
      backoff_schedule = status.backoff_schedule,
      unit = status.unit,
      progress = progress(status.partitions),
      partitions = status.partitions.takeIf { input.include_partitions }?.map { it.toPartition() },
      event_logs = status.event_logs.takeIf { input.include_event_logs }?.map {
        GetBackfillStatusOutput.EventLog(
          occurred_at = it.occurred_at.toString(),
          type = GetBackfillStatusOutput.EventLogType.valueOf(it.type.name),
          user = it.user,
          partition_name = it.partition_name,
          message = it.message,
          extra_data = it.extra_data?.truncate(),
        )
      },
      next_event_log_pagination_token = status.next_offset.takeIf { input.include_event_logs },
      backfila_url = dashboardLinks.backfillRun(input.id),
    )
  }

  private fun progress(partitions: List<UiPartition>) = GetBackfillStatusOutput.Progress(
    partition_count = partitions.size,
    complete_partition_count = partitions.count { it.state == BackfillState.COMPLETE },
    precomputing_done = partitions.all { it.precomputing_done },
    computed_matching_record_count = partitions.sumOf { it.computed_matching_record_count },
    backfilled_matching_record_count = partitions.sumOf { it.backfilled_matching_record_count },
    matching_records_per_minute = partitions
      .filter { it.state == BackfillState.RUNNING }
      .sumOf { it.matching_records_per_minute ?: 0 },
  )

  private fun UiPartition.toPartition() = GetBackfillStatusOutput.Partition(
    name = name,
    state = state.toRunState(),
    precomputing_done = precomputing_done,
    computed_matching_record_count = computed_matching_record_count,
    backfilled_matching_record_count = backfilled_matching_record_count,
    matching_records_per_minute = matching_records_per_minute,
  )

  private fun String.truncate(): String =
    if (length <= MAX_EXTRA_DATA_LENGTH) this else take(MAX_EXTRA_DATA_LENGTH) + TRUNCATION_MARKER
}

// extra_data is a MEDIUMTEXT column (up to 16 MB), and each page returns ten event logs.
internal const val MAX_EXTRA_DATA_LENGTH = 4_000
internal const val TRUNCATION_MARKER = "… [truncated; the Backfila dashboard shows the full text]"
