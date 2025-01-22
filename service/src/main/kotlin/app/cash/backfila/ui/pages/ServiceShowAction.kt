package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.ui.components.AutoReload
import app.cash.backfila.ui.components.BackfillsTable
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.ButtonType
import kotlinx.html.a
import kotlinx.html.button
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.tailwind.Link
import misk.tokens.TokenGenerator
import misk.web.Get
import misk.web.HttpCall
import misk.web.PathParam
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
  ): Response<ResponseBody> {
    if (service.isNullOrBlank()) {
      return Response(
        body = "go to /".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = Headers.headersOf("Location", "/"),
      )
    }
    val variant = variantOrBlank.orEmpty().ifBlank { "default" }

    val backfillRuns = getBackfillRunsAction.backfillRuns(service, variant)

    // TODO show default if other variants and probably link to a switcher
    val label = if (variant == "default") service else "$service ($variant)"
    val htmlResponseBody = dashboardPageLayout.newBuilder()
      .title("$label | Backfila")
      .breadcrumbLinks(
        Link("Services", ServiceIndexAction.PATH),
        Link(label, path),
      )
      .buildHtmlResponseBody {
        AutoReload {
          PageTitle("Service", label) {
            a {
              href = BackfillCreateServiceIndexAction.PATH.replace("{service}", service)
                .replace("{variantOrBlank}", variantOrBlank ?: "")

              button(classes = "rounded-full bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600") {
                type = ButtonType.button
                +"""Create"""
              }
            }
          }

          // TODO Add completed table
          // TODO Add deleted support?
          BackfillsTable(true, backfillRuns.running_backfills)
          BackfillsTable(false, backfillRuns.paused_backfills)
        }
      }

    return Response(htmlResponseBody)
  }

  companion object {
    const val PATH = "/services/{service}/{variantOrBlank}"
  }
}
