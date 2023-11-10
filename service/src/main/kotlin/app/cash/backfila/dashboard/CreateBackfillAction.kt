package app.cash.backfila.dashboard

import app.cash.backfila.BackfillCreator
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.protos.service.CreateBackfillResponse
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

class CreateBackfillAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  private val backfillCreator: BackfillCreator,
) : WebAction {
  @Post("/services/{service}/variants/{variant}/create")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  // TODO allow any user
  @Authenticated(capabilities = ["users"])
  fun create(
    @PathParam service: String,
    @PathParam variant: String,
    @RequestBody request: CreateBackfillRequest,
  ): CreateBackfillResponse {
    return createBackfill(service, variant, request)
  }

  private fun createBackfill(
    service: String,
    variant: String,
    request: CreateBackfillRequest,
  ): CreateBackfillResponse {
    // TODO check user has permissions for this service with access api
    val id = backfillCreator.create(caller.get()!!.user!!, service, variant, request)

    return CreateBackfillResponse(id.id)
  }
}
