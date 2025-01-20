package app.cash.backfila.ui.actions

import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.dashboard.UpdateBackfillAction
import javax.inject.Inject
import javax.inject.Singleton
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.HttpCall
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers

@Singleton
class BackfillCreateHandlerAction @Inject constructor(
  private val startBackfillAction: StartBackfillAction,
  private val stopBackfillAction: StopBackfillAction,
  private val updateBackfillAction: UpdateBackfillAction,
  private val httpCall: ActionScoped<HttpCall>,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
//    @QueryParam service: String,
//    @QueryParam variant: String,
//    @QueryParam backfillName: String,
//    @QueryParam dryRun: Boolean?,
//    @QueryParam rangeStart: String?,
//    @QueryParam rangeEnd: String?,
//    @QueryParam batchSize: String?,
//    @QueryParam scanSize: String?,
//    @QueryParam extraSleepMs: String?,
//    @QueryParam backoffSchedule: String?,
//    // TODO need to make this generic to work with any named custom params
//    @QueryParam customParameter_mealDelayMs: String?,
  ): Response<ResponseBody> {
    // Parse form
    val formFieldName = this.httpCall.get().asOkHttpRequest().url.queryParameterNames


    // Submit create call



    // TODO get created backfill id and redirect to that page on create/clone
    val id = 2




    return Response(
      body = "go to /backfills/$id".toResponseBody(),
      statusCode = 303,
      headers = Headers.headersOf("Location", "/backfills/$id"),
    )
  }

  companion object {
    const val PATH = "/api/backfill/create"
  }
}
