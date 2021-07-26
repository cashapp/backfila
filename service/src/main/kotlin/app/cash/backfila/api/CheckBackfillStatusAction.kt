package app.cash.backfila.api

import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.protos.service.CheckBackfillStatusRequest
import app.cash.backfila.protos.service.CheckBackfillStatusResponse
import app.cash.backfila.protos.service.CheckBackfillStatusResponse.Status
import app.cash.backfila.service.persistence.BackfillState
import javax.inject.Inject
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class CheckBackfillStatusAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  private val getBackfillStatusAction: GetBackfillStatusAction,
) : WebAction {
  @Post("/check-backfill-status")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  // TODO authenticate but any service
  @Unauthenticated
  fun checkBackfillStatus(
    @RequestBody request: CheckBackfillStatusRequest
  ): CheckBackfillStatusResponse {
    val response = getBackfillStatusAction.status(request.backfill_run_id)
    return CheckBackfillStatusResponse(toApiStatus(response.state))
  }

  private fun toApiStatus(backfillState: BackfillState): Status {
    return when (backfillState) {
      BackfillState.PAUSED -> Status.PAUSED
      BackfillState.RUNNING -> Status.RUNNING
      BackfillState.COMPLETE -> Status.COMPLETE
    }
  }
}
