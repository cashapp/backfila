package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.GetServicesAction
import app.cash.backfila.ui.actions.ServiceAutocompleteAction
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import app.cash.backfila.ui.components.ServiceSelect
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.div
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

@Singleton
class BackfillCreateIndexAction @Inject constructor(
  private val serviceAutocompleteAction: ServiceAutocompleteAction,
  private val getBackfillStatusAction: GetBackfillStatusAction,
  private val dashboardPageLayout: DashboardPageLayout,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(): Response<ResponseBody> {
    val htmlResponseBody = dashboardPageLayout.newBuilder()
      .title("Create Backfill | Backfila")
      .buildHtmlResponseBody {
        PageTitle("Create Backfill")

        // If service + variant is blank, show service selection
        val services: Map<String, GetServicesAction.UiService> = serviceAutocompleteAction.getFlattenedServices()
        ServiceSelect(services) { service, variant ->
          BackfillCreateAction.PATH.replace("{service}", service).replace("{variantOrBlank}", variant ?: "")
        }
      }

    return Response(htmlResponseBody)
  }

  companion object {
    const val PATH = "/backfills/create/"
  }
}
