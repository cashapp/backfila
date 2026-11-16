package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetRegisteredBackfillsAction
import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import app.cash.backfila.ui.components.RegisteredBackfillsList
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.net.HttpURLConnection
import kotlinx.html.span
import misk.security.authz.Authenticated
import misk.tailwind.Link
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers

@Singleton
class BackfillCreateServiceIndexAction @Inject constructor(
  private val getRegisteredBackfillsAction: GetRegisteredBackfillsAction,
  private val dashboardPageLayout: DashboardPageLayout,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam service: String,
    @PathParam variantOrBlank: String? = "",
  ): Response<ResponseBody> {
    if (variantOrBlank.orEmpty().contains(".") || variantOrBlank.orEmpty().toIntOrNull() != null) {
      // This means variant is default and the value provided is the backfill name or backfill ID to clone, redirect accordingly
      val newPath = BackfillCreateAction.path(
        service = service,
        variantOrBackfillNameOrId = variantOrBlank.orEmpty(),
        backfillNameOrIdOrBlank = "",
      )
      return Response(
        body = "go to $newPath".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = Headers.headersOf("Location", newPath),
      )
    }

    val variant = variantOrBlank.orEmpty().ifBlank { "default" }
    val label = if (variant == "default") service else "$service ($variant)"
    val htmlResponseBody = dashboardPageLayout.newBuilder()
      .title("Create Backfill | Backfila")
      .breadcrumbLinks(
        Link("Services", ServiceIndexAction.PATH),
        Link(
          label,
          ServiceShowAction.path(service, variantOrBlank),
        ),
        Link(
          "Create",
          PATH
            .replace("{service}", service)
            .replace("{variantOrBlank}", variantOrBlank ?: ""),
        ),
      )
      .buildHtmlResponseBody {
        val registeredBackfills = getRegisteredBackfillsAction.backfills(service, variant)

        PageTitle("Create Backfill") {
          span("inline-flex shrink-0 items-center rounded-full bg-blue-50 px-2.5 py-0.5 text-s font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20") {
            val suffix = if (variantOrBlank.isNullOrBlank()) "" else "/$variantOrBlank"
            +"$service$suffix"
          }
        }

        if (registeredBackfills.backfills.isEmpty()) {
          // TODO add link to runbook?
          AlertError("No backfills registered for this service. Check docs for how to register backfills.")
        } else {
          RegisteredBackfillsList(registeredBackfills.backfills, service, variantOrBlank)
        }
      }

    return Response(htmlResponseBody)
  }

  companion object {
    private const val PATH = "/backfills/create/{service}/{variantOrBlank}"
    fun path(service: String, variantOrBlank: String?) = PATH
      .replace("{service}", service)
      .replace("{variantOrBlank}", variantOrBlank ?: "")
  }
}
