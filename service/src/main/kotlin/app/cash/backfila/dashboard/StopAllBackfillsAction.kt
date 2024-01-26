package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import javax.inject.Inject
import misk.MiskCaller
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger

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
  @Authenticated(capabilities = ["users"])
  fun stopAll(
    @Suppress("unused") @RequestBody
    request: StopAllBackfillsRequest,
  ): StopAllBackfillsResponse {
    // TODO check user has permissions for this service with access api
    logger.info { "Stop all backfills called by ${caller.get()?.user}" }

    val runningBackfills = transacter.transaction { session ->
      queryFactory.newQuery<BackfillRunQuery>()
        .state(BackfillState.RUNNING)
        .list(session)
    }

    runningBackfills.forEach { backfill ->
      logger.info { "Stop backfill ${backfill.id} by ${caller.get()?.user}" }
      backfillStateToggler.toggleRunningState(backfill.id.id, caller.get()!!, BackfillState.PAUSED)
    }

    return StopAllBackfillsResponse()
  }

  companion object {
    private val logger = getLogger<StopAllBackfillsAction>()
  }
}
