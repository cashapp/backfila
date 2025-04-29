package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.ui.components.AutoReload
import app.cash.backfila.ui.components.BackfillsTable
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import app.cash.backfila.ui.components.Pagination
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.ButtonType
import kotlinx.html.a
import kotlinx.html.button
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.tailwind.Link
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
    @QueryParam offset: String? = null,
    @QueryParam lastOffset: String? = null,
    @QueryParam showDeleted: Boolean = false,
  ): Response<ResponseBody> {
    if (service.isNullOrBlank()) {
      return Response(
        body = "go to /".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = Headers.headersOf("Location", "/"),
      )
    }
    val variant = variantOrBlank.orEmpty().ifBlank { "default" }

    val backfillRuns = getBackfillRunsAction.backfillRuns(
      service = service,
      variant = variant,
      pagination_token = offset,
      show_deleted = showDeleted,
    )

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
              href = BackfillCreateServiceIndexAction.path(service, variantOrBlank)
              button(classes = "rounded-full bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600") {
                type = ButtonType.button
                +"""Create"""
              }
            }
          }

          BackfillsTable(true, backfillRuns.running_backfills)
          BackfillsTable(false, backfillRuns.paused_backfills, showDeleted)
          Pagination(backfillRuns.next_pagination_token, offset, lastOffset, path(service, variantOrBlank))
        }
      }

    return Response(htmlResponseBody)
  }

  companion object {
    private const val PATH = "/services/{service}/{variantOrBlank}"
    fun path(service: String, variantOrBlank: String?) = PATH
      .replace("{service}", service)
      .replace("{variantOrBlank}", variantOrBlank ?: "")
  }
}
