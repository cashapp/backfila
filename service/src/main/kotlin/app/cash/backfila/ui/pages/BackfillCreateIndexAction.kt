package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetServicesAction
import app.cash.backfila.ui.actions.ServiceDataHelper
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import app.cash.backfila.ui.components.ServiceSelect
import javax.inject.Inject
import javax.inject.Singleton
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

@Singleton
class BackfillCreateIndexAction @Inject constructor(
  private val servicesGetter: ServiceDataHelper,
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
        val services: Map<String, GetServicesAction.UiService> = servicesGetter.getFlattenedServices()
        ServiceSelect(services) { service, variant ->
          BackfillCreateServiceIndexAction.path(service, variant)
        }
      }

    return Response(htmlResponseBody)
  }

  companion object {
    const val PATH = "/backfills/create/"
  }
}
