package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import java.time.Instant
import javax.inject.Inject
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.hibernate.pagination.Offset
import misk.hibernate.pagination.Page
import misk.hibernate.pagination.idDescPaginator
import misk.hibernate.pagination.newPager
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

      val runningPartitionSummaries = partitionSummary(session, runningBackfills)
      val runningRegisteredBackfills = registeredBackfills(session, runningBackfills)
      val runningUiBackfills = runningBackfills
        .map {
          dbToUi(
            session,
            it,
            runningPartitionSummaries.getValue(it.id),
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
      val pausedPartitionSummaries = partitionSummary(session, pausedBackfills)
      val pausedUiBackfills = pausedBackfills
        .map {
          dbToUi(
            session,
            it,
            pausedPartitionSummaries.getValue(it.id),
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

  private data class PartitionSummary(
    val precomputingDone: Boolean,
    val totalComputedMatchingRecordCount: Long,
    val totalBackfilledMatchingRecordCount: Long,
  )

  private fun partitionSummary(
    session: Session,
    runs: List<DbBackfillRun>,
  ): Map<Id<DbBackfillRun>, PartitionSummary> {
    val list = session.hibernateSession.createQuery(
      """
        select backfill_run_id,
          sum(precomputing_done) = sum(1),
          sum(computed_matching_record_count),
          sum(backfilled_matching_record_count)
        from DbRunPartition
        where backfill_run_id in (:ids)
        group by backfill_run_id
        """
    ).setParameter("ids", runs.map { it.id })
      .list() as List<Array<Any>>
    return list.associateBy { it[0] as Id<DbBackfillRun> }.mapValues {
      PartitionSummary(
        it.value[1] as Boolean,
        it.value[2] as Long,
        it.value[3] as Long,
      )
    }
  }

  private fun dbToUi(
    @Suppress("UNUSED_PARAMETER") session: Session,
    run: DbBackfillRun,
    partitionSummary: PartitionSummary,
    registeredBackfill: DbRegisteredBackfill,
  ): UiBackfillRun {
    return UiBackfillRun(
      run.id.toString(),
      registeredBackfill.name,
      run.state,
      run.dry_run,
      run.created_at,
      run.created_by_user,
      run.updated_at,
      partitionSummary.precomputingDone,
      partitionSummary.totalComputedMatchingRecordCount,
      partitionSummary.totalBackfilledMatchingRecordCount
    )
  }
}
