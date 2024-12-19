package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import javax.inject.Inject
import misk.exceptions.BadRequestException
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
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger
import java.time.Instant

data class SearchBackfillRunsResponse(
  val running_backfills: List<UiBackfillRun>,
  val paused_backfills: List<UiBackfillRun>,
  val next_pagination_token: String?,
)

class SearchBackfillRunsAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {
  @Get("/services/{service}/variants/{variant}/backfill-runs/search")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated(allowAnyUser = true)
  fun searchBackfillRuns(
    @PathParam service: String,
    @PathParam variant: String,
    @QueryParam pagination_token: String? = null,
    @QueryParam backfill_name: String? = null,
    @QueryParam created_by_user: String? = null,
    @QueryParam created_start_date: Instant? = null,
    @QueryParam created_end_date: Instant? = null,
  ): SearchBackfillRunsResponse {
    val filterArgs = FilterArgs(
      backfillName = backfill_name,
      createdByUser = created_by_user,
      createdStartDate = created_start_date,
      createdEndDate = created_end_date,
    )
    return search(service, variant, pagination_token, filterArgs)
  }

  private fun search(
    service: String,
    variant: String,
    paginationToken: String?,
    filterArgs: FilterArgs,
  ): SearchBackfillRunsResponse {
    return transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .variant(variant)
        .uniqueResult(session) ?: throw BadRequestException("`$service`-`$variant` doesn't exist")

      val runningBackfills = queryFactory.newQuery<BackfillRunQuery>()
        .serviceId(dbService.id)
        .state(BackfillState.RUNNING)
        .orderByIdDesc()
        .filterByArgs(filterArgs)
        .list(session)

      val runningPartitionSummaries = partitionSummary(session, runningBackfills)
      val runningRegisteredBackfills = registeredBackfills(session, runningBackfills)
      val runningUiBackfills = runningBackfills
        .map {
          dbToUi(
            session,
            it,
            runningPartitionSummaries.getValue(it.id),
            runningRegisteredBackfills.getValue(it.registered_backfill_id),
          )
        }

      val (pausedBackfills, nextOffset) = queryFactory.newQuery<BackfillRunQuery>()
        .serviceId(dbService.id)
        .stateNot(BackfillState.RUNNING)
        .filterByArgs(filterArgs)
        .newPager(
          idDescPaginator(),
          initialOffset = paginationToken?.let { Offset(it) },
          pageSize = 20,
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
            pausedRegisteredBackfills.getValue(it.registered_backfill_id),
          )
        }

      SearchBackfillRunsResponse(
        runningUiBackfills,
        pausedUiBackfills,
        next_pagination_token = nextOffset?.offset,
      )
    }
  }

  private fun registeredBackfills(
    session: Session,
    runs: List<DbBackfillRun>,
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
        """,
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
      partitionSummary.totalBackfilledMatchingRecordCount,
    )
  }

  private fun BackfillRunQuery.filterByBackfillNameIfPresent(backfillName: String?): BackfillRunQuery {
    return if (backfillName.isNullOrEmpty()) {
      this
    } else {
      this.constraint { backfillRunRoot ->
        val registeredBackfillJoin = backfillRunRoot.join<DbBackfillRun, DbRegisteredBackfill>("registered_backfill")
        like(registeredBackfillJoin.get("name"), "%$backfillName%")
      }
    }
  }

  private fun BackfillRunQuery.filterByUserCreatedIfPresent(author: String?): BackfillRunQuery {
    return if (author.isNullOrEmpty()) {
      this
    } else {
      this.constraint { backfillRunRoot ->
        like(backfillRunRoot.get("created_by_user"), "%$author%")
      }
    }
  }

  private fun BackfillRunQuery.filterByArgs(filterArgs: FilterArgs): BackfillRunQuery {
    return this.filterByUserCreatedIfPresent(filterArgs.createdByUser)
               .filterByBackfillNameIfPresent(filterArgs.backfillName)
  }

  private data class FilterArgs (
    val backfillName: String? = null,
    val createdByUser: String? = null,
    val createdStartDate: Instant? = null,
    val createdEndDate: Instant? = null,
  )
}
