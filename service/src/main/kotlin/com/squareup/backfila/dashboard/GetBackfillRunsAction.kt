package com.squareup.backfila.dashboard

import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.BackfillRunQuery
import com.squareup.backfila.service.BackfillState
import com.squareup.backfila.service.DbBackfillRun
import com.squareup.backfila.service.ServiceQuery
import misk.exceptions.BadRequestException
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.logging.getLogger
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import java.time.Instant
import javax.inject.Inject

data class UiBackfillRun(
  val id: String,
  val name: String,
  val state: BackfillState,
  val created_at: Instant,
  val created_by_user: String?
//  val precomputing_done: Boolean,
//  val computed_matching_record_count: Long,
//  val backfilled_matching_record_count: Long
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
      // TODO pagination, filtering
      val runningBackfills = queryFactory.newQuery<BackfillRunQuery>()
          .serviceId(dbService.id)
          .state(BackfillState.RUNNING)
          .orderByIdDesc()
          .list(session)
          .map { dbToUi(session, it) }
      val pausedBackfills = queryFactory.newQuery<BackfillRunQuery>()
          .serviceId(dbService.id)
          .stateNot(BackfillState.RUNNING)
          .orderByIdDesc()
          .list(session)
          .map { dbToUi(session, it) }

      GetBackfillRunsResponse(runningBackfills, pausedBackfills, next_pagination_token = null)
    }
  }

  private fun dbToUi(session: Session, run: DbBackfillRun): UiBackfillRun {
    return UiBackfillRun(
        run.id.toString(),
        run.registered_backfill.name,
        run.state,
        run.created_at,
        run.created_by_user
    )
  }

  companion object {
    private val logger = getLogger<GetBackfillRunsAction>()
  }
}
