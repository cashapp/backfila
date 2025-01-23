package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetServicesAction
import app.cash.backfila.ui.actions.ServiceDataHelper
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import app.cash.backfila.ui.components.ServiceSelect
import javax.inject.Inject
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class ServiceIndexAction @Inject constructor(
  private val serviceDataHelper: ServiceDataHelper,
  private val dashboardPageLayout: DashboardPageLayout,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(): String = dashboardPageLayout
    .newBuilder()
    .title("Backfila Home")
    .build {
      PageTitle("Services")

      // Search and select from Services
      val services: Map<String, GetServicesAction.UiService> = serviceDataHelper.getFlattenedServices()
      ServiceSelect(services) { service, variant ->
        ServiceShowAction.PATH.replace("{service}", service).replace("{variantOrBlank}", variant ?: "")
      }
    }

  companion object {
    const val PATH = "/services/"
  }
}
