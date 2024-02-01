package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.AlertSupport
import app.cash.backfila.ui.components.DashboardLayout
import app.cash.backfila.ui.components.PageTitle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.p
import misk.hotwire.buildHtmlResponseBody
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

@Singleton
class BackfillShowAction @Inject constructor(
  private val config: BackfilaConfig,
  private val getBackfillStatusAction: GetBackfillStatusAction,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Unauthenticated
  fun get(
    @PathParam id: String,
  ): Response<ResponseBody> {
    if (id.toLongOrNull() == null) {
      return Response(
        buildHtmlResponseBody {
          DashboardLayout(
            title = "Backfill $id | Backfila",
            path = PATH,
          ) {
            PageTitle("Backfill", id)
            AlertError("Invalid Backfill Id [id=$id], must be of type Long.")
            AlertSupport(config.support_button_label, config.support_button_url)
          }
        },
      )
    }
    val backfill = getBackfillStatusAction.status(id.toLong())

    val htmlResponseBody = buildHtmlResponseBody {
      DashboardLayout(
        title = "Backfill $id | Backfila",
        path = PATH,
      ) {
        PageTitle("Backfill", id)

        p {
          +"""$backfill"""
        }

        AlertSupport(config.support_button_label, config.support_button_url)
      }
    }

    return Response(htmlResponseBody)
  }

  companion object {
    const val PATH = "/backfills/{id}"
  }
}
