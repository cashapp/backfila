package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.ui.PathBuilder
import app.cash.backfila.ui.components.AlertSupport
import app.cash.backfila.ui.components.DashboardLayout
import app.cash.backfila.ui.components.PageTitle
import app.cash.backfila.ui.components.collapseBackfillName
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h3
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
  fun checkService(
    @PathParam id: Long,
  ): Response<ResponseBody> {
    val status = getBackfillStatusAction.status(id)

    val htmlResponseBody = buildHtmlResponseBody {
      DashboardLayout(
        title = "Backfill $id | ${status.service_name} | Backfila",
        path = PATH,
      ) {
        h3("text-green-500 ") {
          a(classes = "hover:underline") {
            href = PathBuilder(
              path = ServiceIndexAction.PATH,
            ).build()
            +"""All Services"""
          }
          +"""  >  """
          a(classes = "hover:underline") {
            href = PathBuilder(
              path = ServiceShowAction.PATH
                .replace("{service}", status.service_name)
                .replace("{variant}", status.variant),
            ).build()
            val variantSuffix = if (status.variant == "default") "" else "(${status.variant})"
            +"""${status.service_name}$variantSuffix"""
          }
        }
        PageTitle("Backfill #$id", status.name.collapseBackfillName())
        div("mx-auto max-w-7xl px-200 sm:px-6 lg:px-8s") {
          p {
            +"""State: ${status.state}"""
          }
          p {
            +"""Dry run: ${status.dry_run}"""
          }
          p {
            +"""Threads per partition: ${status.num_threads}"""
          }
          p {
            +"""Scan Size: ${status.scan_size}"""
          }
          p {
            +"""Batch Size: ${status.batch_size}"""
          }
          p {
            +"""Sleep between batches (ms): ${status.extra_sleep_ms}"""
          }
          p {
            +"""Created: ${status.created_at} by ${status.created_by_user}"""
          }
          p {
            +"""View Logs"""
          }
          status.parameters?.map { (key, value) ->
            p {
              +"""$key: $value"""
            }
          }
          p {
            +"""Total backfilled records: """
          }
          p {
            +"""Total records to run: """
          }
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
