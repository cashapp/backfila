package app.cash.backfila.api

import app.cash.backfila.BackfillCreator
import app.cash.backfila.dashboard.BackfillStateToggler
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateAndStartBackfillResponse
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

class CreateAndStartBackfillAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  private val backfillCreator: BackfillCreator,
  private val backfillStateToggler: BackfillStateToggler,
) : WebAction {
  @Post("/create-and-start-backfill")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  // TODO authenticate but any service
  @Unauthenticated
  fun createAndStartBackfill(
    @RequestBody request: CreateAndStartBackfillRequest,
  ): CreateAndStartBackfillResponse {
    val caller = caller.get()!!
    val service = caller.service!!

    val id = backfillCreator.create(service, service, request.create_request)

    backfillStateToggler.toggleRunningState(id.id, caller, BackfillState.RUNNING)

    return CreateAndStartBackfillResponse(id.id)
  }
}
