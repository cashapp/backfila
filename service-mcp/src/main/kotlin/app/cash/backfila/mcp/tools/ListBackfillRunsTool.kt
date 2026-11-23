package app.cash.backfila.mcp.tools

import app.cash.backfila.mcp.DashboardLinks
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.DbService
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.serialization.Serializable
import misk.MiskCaller
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.constraint
import misk.hibernate.newQuery
import misk.hibernate.pagination.Offset
import misk.hibernate.pagination.Page
import misk.hibernate.pagination.idDescPaginator
import misk.hibernate.pagination.newPager
import misk.mcp.Description
import misk.scope.ActionScoped

@Serializable
internal data class ListBackfillRunsInput(
  @Description("Only include runs for this service, from list_services. Leave it out to search every service.")
  val service: String? = null,
  @Description("Only include runs for this variant, from list_services. Leave it out to include every variant.")
  val variant: String? = null,
  @Description("Only include runs whose backfill name contains this text, ignoring case.")
  val backfill_name: String? = null,
  @Description("Only include runs created by this username.")
  val created_by_user: String? = null,
  @Description("Only include runs created by the user you're acting for.")
  val created_by_me: Boolean = false,
  @Description("Only include runs in these states, such as [\"RUNNING\"] for runs in progress. Leave it out to include every state.")
  val states: List<RunState> = listOf(),
  @Description("Only include runs created at or after this time, as an ISO-8601 timestamp or date.")
  val created_after: String? = null,
  @Description("Include runs that were deleted from the dashboard.")
  val show_deleted: Boolean = false,
  @Description("next_pagination_token from a previous call with the same filters, to get the next page.")
  val pagination_token: String? = null,
)

@Serializable
internal data class ListBackfillRunsOutput(
  @Description("Matching runs, newest first, up to $RUNS_PAGE_SIZE at a time.")
  val runs: List<Run>,
  @Description("Pass as pagination_token to get the next page. Absent on the last page.")
  val next_pagination_token: String?,
) {
  @Serializable
  data class Run(
    @Description("Backfill run ID. Pass it to get_backfill_status for details.")
    val id: Long,
    val service: String,
    val variant: String,
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
    @Description("Backfilled share of the computed records, from 0 to 100. Overstated until precomputing_done is true. Absent before any records are computed.")
    val percent_complete: Double?,
    @Description("When the run last logged an error, as an ISO-8601 timestamp. Absent if it never has. Pass event_log_type ERROR to get_backfill_status for details.")
    val last_error_at: String?,
    @Description("The run's page in Backfila, where a user can view, start, pause, or edit it. Absent if Backfila's URL isn't configured.")
    val backfila_url: String?,
  )
}

@Singleton
internal class ListBackfillRunsTool @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val caller: ActionScoped<MiskCaller?>,
  private val dashboardLinks: DashboardLinks,
) : BackfilaMcpTool<ListBackfillRunsInput, ListBackfillRunsOutput>() {
  override val name = "list_backfill_runs"
  override val title = "List backfill runs"
  override val description =
    "Finds backfill runs across every service, newest first. Filter by service, backfill name, creator, state, or " +
      "creation time. For example, created_by_me with states [\"RUNNING\"] finds the user's runs in progress. " +
      "Each run's last_error_at shows whether it has logged errors."
  override val readOnlyHint = true
  override val openWorldHint = false

  // Reads the database directly because the dashboard's GetBackfillRunsAction only searches one
  // service variant, and its name filter is case-sensitive.
  override suspend fun call(input: ListBackfillRunsInput): ListBackfillRunsOutput {
    val paginationToken = paginationToken("pagination_token", input.pagination_token)
    val createdAfter = input.created_after?.takeIf { it.isNotBlank() }?.let(::parseCreatedAfter)
    val createdBy = createdBy(input)
    return transacter.transaction { session ->
      val serviceIds = serviceIds(session, input.service?.ifBlank { null }, input.variant?.ifBlank { null })
      val backfillIds = input.backfill_name?.ifBlank { null }?.let { matchingBackfillIds(session, serviceIds, it) }
      if (backfillIds?.isEmpty() == true) return@transaction ListBackfillRunsOutput(listOf(), next_pagination_token = null)

      val (runs, nextOffset) = queryFactory.newQuery<BackfillRunQuery>()
        .apply { if (serviceIds != null) serviceIdIn(serviceIds) }
        .apply {
          if (backfillIds != null) constraint { root -> root.get<Id<DbRegisteredBackfill>>("registered_backfill_id").`in`(backfillIds) }
        }
        .apply { if (createdBy != null) createdByUser(createdBy) }
        .apply {
          if (input.states.isNotEmpty()) constraint { root -> root.get<BackfillState>("state").`in`(input.states.map { it.toBackfillState() }) }
        }
        .apply { if (createdAfter != null) constraint { root -> greaterThanOrEqualTo(root.get("created_at"), createdAfter) } }
        .apply { if (!input.show_deleted) notSoftDeleted() }
        .newPager(idDescPaginator(), initialOffset = paginationToken?.let(::Offset), pageSize = RUNS_PAGE_SIZE)
        .nextPage(session) ?: Page.empty()

      val progress = progress(session, runs)
      val lastErrors = lastErrors(session, runs)
      ListBackfillRunsOutput(runs.map { it.toRun(progress[it.id], lastErrors[it.id]) }, nextOffset?.offset)
    }
  }

  private fun createdBy(input: ListBackfillRunsInput): String? {
    val user = input.created_by_user?.ifBlank { null }
    if (!input.created_by_me) return user
    if (user != null) throw ToolError("Pass created_by_user or created_by_me, not both.")
    return caller.get()?.user ?: throw ToolError("created_by_me needs a signed-in user, but this caller is a service.")
  }

  /** Returns null when neither filter is set, meaning every service. */
  private fun serviceIds(session: Session, service: String?, variant: String?): Set<Id<DbService>>? {
    if (service == null && variant == null) return null
    val services = queryFactory.newQuery<ServiceQuery>()
      .apply { if (service != null) registryName(service) }
      .apply { if (variant != null) variant(variant) }
      .list(session)
    if (services.isEmpty()) {
      throw ToolError("No service matches ${listOfNotNull(service, variant).joinToString("-") { "`$it`" }}. $UNKNOWN_SERVICE_HINT")
    }
    return services.map { it.id }.toSet()
  }

  private fun matchingBackfillIds(session: Session, serviceIds: Set<Id<DbService>>?, name: String) =
    queryFactory.newQuery<RegisteredBackfillQuery>()
      .apply { if (serviceIds != null) constraint { root -> root.get<Id<DbService>>("service_id").`in`(serviceIds) } }
      .list(session)
      .filter { it.name.contains(name, ignoreCase = true) }
      .map { it.id }
      .toSet()

  private fun progress(session: Session, runs: List<DbBackfillRun>): Map<Id<DbBackfillRun>, RunProgress> {
    if (runs.isEmpty()) return mapOf()
    @Suppress("UNCHECKED_CAST")
    val rows = session.hibernateSession.createQuery(
      """
        select backfill_run_id,
          sum(case when precomputing_done = true then 1 else 0 end) = sum(1),
          sum(computed_matching_record_count),
          sum(backfilled_matching_record_count)
        from DbRunPartition
        where backfill_run_id in (:ids)
        group by backfill_run_id
      """,
    ).setParameter("ids", runs.map { it.id }).list() as List<Array<Any>>
    return rows.associate { (it[0] as Id<DbBackfillRun>) to RunProgress(it[1] as Boolean, it[2] as Long, it[3] as Long) }
  }

  private fun lastErrors(session: Session, runs: List<DbBackfillRun>): Map<Id<DbBackfillRun>, Instant> {
    if (runs.isEmpty()) return mapOf()
    @Suppress("UNCHECKED_CAST")
    val rows = session.hibernateSession.createQuery(
      """
        select backfill_run_id, max(created_at)
        from DbEventLog
        where type = :type and backfill_run_id in (:ids)
        group by backfill_run_id
      """,
    ).setParameter("type", DbEventLog.Type.ERROR).setParameter("ids", runs.map { it.id }).list() as List<Array<Any>>
    return rows.associate { (it[0] as Id<DbBackfillRun>) to (it[1] as Instant) }
  }

  private fun DbBackfillRun.toRun(progress: RunProgress?, lastError: Instant?): ListBackfillRunsOutput.Run {
    val computed = progress?.computed ?: 0
    val backfilled = progress?.backfilled ?: 0
    return ListBackfillRunsOutput.Run(
      id = id.id,
      service = service.registry_name,
      variant = service.variant,
      backfill_name = registered_backfill.name,
      state = state.toRunState(),
      dry_run = dry_run,
      created_at = created_at.toString(),
      created_by_user = created_by_user,
      last_active_at = updated_at.toString(),
      precomputing_done = progress?.precomputingDone ?: false,
      computed_matching_record_count = computed,
      backfilled_matching_record_count = backfilled,
      percent_complete = percentComplete(backfilled, computed),
      last_error_at = lastError?.toString(),
      backfila_url = dashboardLinks.backfillRun(id.id),
    )
  }

  private data class RunProgress(val precomputingDone: Boolean, val computed: Long, val backfilled: Long)
}

private fun parseCreatedAfter(value: String): Instant = runCatching { Instant.parse(value) }
  .recoverCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant() }
  .getOrElse {
    throw ToolError("created_after must be an ISO-8601 timestamp, such as 2026-01-31T00:00:00Z, or a date, such as 2026-01-31.")
  }

internal const val RUNS_PAGE_SIZE = 20
