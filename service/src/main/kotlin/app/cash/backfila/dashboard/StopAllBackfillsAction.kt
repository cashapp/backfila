package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import javax.inject.Inject
import misk.MiskCaller
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.logging.getLogger
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class StopAllBackfillsRequest
class StopAllBackfillsResponse

class StopAllBackfillsAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  private val backfillStateToggler: BackfillStateToggler,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {

  @Post("/backfills/stop_all")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated(allowAnyUser = true)
  fun stopAll(
    @Suppress("unused") @RequestBody
    request: StopAllBackfillsRequest,
  ): StopAllBackfillsResponse {
    // TODO check user has permissions for this service with access api
    logger.info { "Stop all backfills called by ${caller.get()?.user}" }

    val runningBackfillIds = transacter.transaction { session ->
      queryFactory.newQuery<BackfillRunQuery>()
        .state(BackfillState.RUNNING)
        .list(session)
        .map { it.id.id }
    }

    runningBackfillIds.forEach { backfillId ->
      logger.info { "Stop backfill $backfillId by ${caller.get()?.user}" }
      backfillStateToggler.toggleRunningState(backfillId, caller.get()!!, BackfillState.PAUSED)
    }

    return StopAllBackfillsResponse()
  }

  companion object {
    private val logger = getLogger<StopAllBackfillsAction>()
  }
}
