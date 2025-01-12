package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.ui.components.BackfillsTable
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.div
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.tokens.TokenGenerator
import misk.web.Get
import misk.web.HttpCall
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers

@Singleton
class ServiceShowAction @Inject constructor(
  private val clientHttpCall: ActionScoped<HttpCall>,
  private val dashboardPageLayout: DashboardPageLayout,
  private val getBackfillRunsAction: GetBackfillRunsAction,
  private val tokenGenerator: TokenGenerator,
) : WebAction {
  private val path by lazy {
    clientHttpCall.get().url.encodedPath
  }

  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam service: String?,
    @PathParam variantOrBlank: String? = "",
    @QueryParam("experimental") experimental: Boolean? = false,
  ): Response<ResponseBody> {
    if (service.isNullOrBlank()) {
      return Response(
        body = "go to /".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = Headers.headersOf("Location", "/"),
      )
    }
    val variant = variantOrBlank?.ifBlank { "default" } ?: "default"

    val backfillRuns = getBackfillRunsAction.backfillRuns(service, variant)

    // TODO show default if other variants and probably link to a switcher
    val label = if (variant == "default") service else "$service ($variant)"
    val htmlResponseBody = dashboardPageLayout.newBuilder()
      .title("$label | Backfila")
      .buildHtmlResponseBody {
        div {
          attributes["data-controller"] = "auto-reload"
          attributes["data-auto-reload-target"] = "frame"

          PageTitle("Service", label)

          // TODO Add completed table
          // TODO Add deleted support?
//          turbo_frame(id = "backfill-tables", src = path.replace("services", "services/progress/${tokenGenerator.generate()}")) {
          BackfillsTable(true, backfillRuns.running_backfills)
          BackfillsTable(false, backfillRuns.paused_backfills)
//          }
        }
      }

    return Response(htmlResponseBody)
  }

  companion object {
    const val PATH = "/services/{service}/{variantOrBlank}"
  }
}
