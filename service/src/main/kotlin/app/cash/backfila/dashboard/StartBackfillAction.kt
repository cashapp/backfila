package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfillState
import javax.inject.Inject
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger

class StartBackfillRequest
class StartBackfillResponse

class StartBackfillAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  private val backfillStateToggler: BackfillStateToggler
) : WebAction {

  @Post("/backfills/{id}/start")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  // TODO allow any user
  @Authenticated(capabilities = ["users"])
  fun start(
    @PathParam id: Long,
    @Suppress("unused")
    @RequestBody request: StartBackfillRequest
  ): StartBackfillResponse {
    // TODO check user has permissions for this service with access api
    logger.info { "Start backfill $id by ${caller.get()?.user}" }
    backfillStateToggler.toggleRunningState(id, caller.get()!!, BackfillState.RUNNING)
    return StartBackfillResponse()
  }

  companion object {
    private val logger = getLogger<StartBackfillAction>()
  }
}
