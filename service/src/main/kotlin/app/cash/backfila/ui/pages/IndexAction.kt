package app.cash.backfila.ui.pages

import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.DashboardLayout
import app.cash.backfila.ui.components.PageTitle
import app.cash.backfila.ui.components.ServiceAutocompleteWrapper
import misk.hotwire.buildHtml
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject

class IndexAction @Inject constructor() : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Unauthenticated
  fun get(
    @QueryParam s: String?,
    @QueryParam e: String?,
  ): String {
    return buildHtml {
      DashboardLayout(
        title = "Monitor Checkup",
        path = PATH,
      ) {
        PageTitle("Monitor Checkup", s)
        e?.let { AlertError(it) }
        ServiceAutocompleteWrapper(s, ServiceStatusAction.PATH)
      }
    }
  }

  companion object {
    const val PATH = "/"
  }
}
