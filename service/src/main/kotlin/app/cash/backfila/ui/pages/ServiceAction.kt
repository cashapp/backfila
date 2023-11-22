package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.ui.components.AlertInfoHighlight
import app.cash.backfila.ui.components.BackfillsTable
// import app.cash.backfila.ui.components.CollapsibleMonitorList
import app.cash.backfila.ui.components.DashboardLayout
import app.cash.backfila.ui.components.PageTitle
import com.squareup.cash.monitorcheckup.ui.SLACK_CHANNEL_NAME
import com.squareup.cash.monitorcheckup.ui.SLACK_CHANNEL_URL
import com.squareup.wire.toHttpUrl
import java.net.HttpURLConnection.HTTP_MOVED_TEMP
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.a
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.ul
import misk.hotwire.buildHtmlResponseBody
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.QueryParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers

@Singleton
class ServiceAction @Inject constructor(
  private val getBackfillRunsAction: GetBackfillRunsAction,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Unauthenticated
  fun checkService(
    @QueryParam("s") serviceName: String?,
    @QueryParam("experimental") experimental: Boolean? = false,
  ): Response<ResponseBody> {
    if (serviceName == null) {
      // Redirect back to home if service not found with an error message
      val errorMessage = "Service '$serviceName' not found. Try a new search below."
      val encodedQuery = "https://localhost/?e=$errorMessage".toHttpUrl().encodedQuery
      return Response(
        body = "go to /?$encodedQuery".toResponseBody(),
        statusCode = HTTP_MOVED_TEMP,
        headers = Headers.headersOf("Location", "/?$encodedQuery"),
      )
    }

    val backfillRuns = getBackfillRunsAction.backfillRuns(serviceName)

    val htmlResponseBody = buildHtmlResponseBody {
      DashboardLayout(
        title = "$serviceName | Monitor Checkup",
        path = PATH,
      ) {
        PageTitle("Service", serviceName)



        BackfillsTable(true, backfillRuns.running_backfills)
        BackfillsTable(false, backfillRuns.paused_backfills)




        ul("space-y-3") {
          role = "list"


          AlertInfoHighlight(
            "Questions? Concerns? Contact us on Slack.",
            SLACK_CHANNEL_NAME,
            SLACK_CHANNEL_URL,
            spaceAbove = true,
          )

          p("px-4 py-5 text-xs") {
            +"Computed using commit "
            a(classes = "text-green-500 hover:underline") {
              // TODO investigate how this is calculated, might be hardcoding to monitor-checkup repo
              href = "#/commits/"
              +"yo"
            }
            +"."
          }
        }
      }
    }

    return Response(htmlResponseBody)
  }

  companion object {
    const val PATH = "/service/{service}"
  }
}
