package app.cash.backfila.ui.pages

import app.cash.backfila.ui.SLACK_CHANNEL_NAME
import app.cash.backfila.ui.SLACK_CHANNEL_URL
import app.cash.backfila.ui.components.AlertInfoHighlight
import app.cash.backfila.ui.components.AlertSlackHelp
import app.cash.backfila.ui.components.DashboardLayout
import app.cash.backfila.ui.components.PageTitle
import javax.inject.Inject
import kotlinx.html.div
import kotlinx.html.role
import kotlinx.html.ul
import misk.hotwire.buildHtml
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class ListMonitorsAction @Inject constructor() : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Unauthenticated
  fun listMonitors(): String {
    return buildHtml {
      DashboardLayout(
        title = "Backfila",
        path = ServiceIndexAction.PATH,
      ) {
        PageTitle("Backfila", "Recommended monitors")

        div("pb-8") {
          +"These are all the monitors that monitor-checkup will recommend for any "
          +"Cash service, if the service meets the right criteria."
        }

        ul("space-y-3") {
          role = "list"
        }

        AlertSlackHelp()
      }
    }
  }

  companion object {
    const val PATH = "/list-monitors"
  }
}
