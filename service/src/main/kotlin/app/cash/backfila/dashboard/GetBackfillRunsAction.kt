package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.DbRunPartition
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.RunPartitionQuery
import app.cash.backfila.service.persistence.ServiceQuery
import java.time.Instant
import javax.inject.Inject
import misk.exceptions.BadRequestException
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.hibernate.pagination.Offset
import misk.hibernate.pagination.Page
import misk.hibernate.pagination.idDescPaginator
import misk.hibernate.pagination.newPager
import misk.logging.getLogger
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

data class UiBackfillRun(
  val id: String,
  val name: String,
  val state: BackfillState,
  val dry_run: Boolean,
  val created_at: Instant,
  val created_by_user: String?,
  val last_active_at: Instant,
  val precomputing_done: Boolean,
  val computed_matching_record_count: Long,
  val backfilled_matching_record_count: Long
)

data class GetBackfillRunsResponse(
  val running_backfills: List<UiBackfillRun>,
  val paused_backfills: List<UiBackfillRun>,
  val next_pagination_token: String?
)

class GetBackfillRunsAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : WebAction {
  @Get("/services/{service}/backfill-runs")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated
  fun backfillRuns(
    @PathParam service: String,
    @QueryParam pagination_token: String? = null
  ): GetBackfillRunsResponse {
    return transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .uniqueResult(session) ?: throw BadRequestException("`$service` doesn't exist")

      val runningBackfills = queryFactory.newQuery<BackfillRunQuery>()
        .serviceId(dbService.id)
        .state(BackfillState.RUNNING)
        .orderByIdDesc()
        .list(session)

      val runningPartitions = partitions(session, runningBackfills)
      val runningRegisteredBackfills = registeredBackfills(session, runningBackfills)
      val runningUiBackfills = runningBackfills
        .map {
          dbToUi(
            session,
            it,
            runningPartitions.getValue(it.id),
            runningRegisteredBackfills.getValue(it.registered_backfill_id)
          )
        }

      val (pausedBackfills, nextOffset) = queryFactory.newQuery<BackfillRunQuery>()
        .serviceId(dbService.id)
        .stateNot(BackfillState.RUNNING)
        .newPager(
          idDescPaginator(),
          initialOffset = pagination_token?.let { Offset(it) },
          pageSize = 20
        )
        .nextPage(session) ?: Page.empty()

      val pausedRegisteredBackfills = registeredBackfills(session, pausedBackfills)
      val pausedPartitions = partitions(session, pausedBackfills)
      val pausedUiBackfills = pausedBackfills
        .map {
          dbToUi(
            session,
            it,
            pausedPartitions.getValue(it.id),
            pausedRegisteredBackfills.getValue(it.registered_backfill_id)
          )
        }

      GetBackfillRunsResponse(
        runningUiBackfills,
        pausedUiBackfills,
        next_pagination_token = nextOffset?.offset
      )
    }
  }

  private fun registeredBackfills(
    session: Session,
    runs: List<DbBackfillRun>
  ) = queryFactory.newQuery<RegisteredBackfillQuery>()
    .idIn(runs.map { it.registered_backfill_id }.toSet())
    .list(session)
    .associateBy { it.id }

  private fun partitions(
    session: Session,
    pausedBackfills: List<DbBackfillRun>
  ) = queryFactory.newQuery<RunPartitionQuery>()
    .backfillRunIdIn(pausedBackfills.map { it.id })
    .list(session)
    .groupBy { it.backfill_run_id }

  private fun dbToUi(
    @Suppress("UNUSED_PARAMETER") session: Session,
    run: DbBackfillRun,
    partitions: List<DbRunPartition>,
    registeredBackfill: DbRegisteredBackfill
  ): UiBackfillRun {
    val precomputingDone = partitions.all { it.precomputing_done }
    val computedMatchingRecordCount = partitions
      .map { it.computed_matching_record_count }
      .sum()
    val backfilledMatchingRecordCount = partitions
      .map { it.backfilled_matching_record_count }
      .sum()
    return UiBackfillRun(
      run.id.toString(),
      registeredBackfill.name,
      run.state,
      run.dry_run,
      run.created_at,
      run.created_by_user,
      run.updated_at,
      precomputingDone,
      computedMatchingRecordCount,
      backfilledMatchingRecordCount
    )
  }

  companion object {
    private val logger = getLogger<GetBackfillRunsAction>()
  }
}
