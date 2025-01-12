package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.ui.components.BackfillsTable
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.role
import kotlinx.html.ul
import misk.security.authz.Authenticated
import misk.web.Get
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
  private val getBackfillRunsAction: GetBackfillRunsAction,
  private val dashboardPageLayout: DashboardPageLayout,
) : WebAction {
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
        PageTitle("Service", label)

        // TODO Add completed table
        // TODO Add deleted support?
        BackfillsTable(true, backfillRuns.running_backfills)
        BackfillsTable(false, backfillRuns.paused_backfills)

        ul("space-y-3") {
          role = "list"
        }
      }

    return Response(htmlResponseBody)
  }

  companion object {
    const val PATH = "/services/{service}/{variantOrBlank}"
  }
}
