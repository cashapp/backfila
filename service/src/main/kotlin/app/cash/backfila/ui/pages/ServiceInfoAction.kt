package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetServiceDetailsAction
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import java.net.HttpURLConnection
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.div
import kotlinx.html.pre
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

@Singleton
class ServiceInfoAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
  private val getServiceDetailsAction: GetServiceDetailsAction,
) : WebAction {

  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam service: String?,
    @PathParam variantOrBlank: String? = "",
  ): Response<ResponseBody> {
    if (service.isNullOrBlank()) {
      return Response(
        body = "Service name is required".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_BAD_REQUEST,
      )
    }

    val variant = variantOrBlank.orEmpty().ifBlank { "default" }

    try {
      val serviceDetails = getServiceDetailsAction.getServiceDetails(service, variant)
      val label = if (variant == "default") service else "$service ($variant)"

      val htmlResponseBody = dashboardPageLayout.newBuilder()
        .title("$label Info | Backfila")
        .breadcrumbLinks(
          Link("Services", ServiceIndexAction.PATH),
          Link(label, ServiceShowAction.path(service, variantOrBlank)),
          Link("Info", path(service, variantOrBlank)),
        )
        .buildHtmlResponseBody {
          PageTitle("Service Information", label)

          div("bg-white shadow rounded-lg") {
            div("px-4 py-5 sm:p-6") {
              div("space-y-6") {
                div {
                  span("text-sm font-medium text-gray-500") { +"Service Name" }
                  div("mt-1 text-sm text-gray-900 font-semibold") {
                    +serviceDetails.service_name
                  }
                }

                div {
                  span("text-sm font-medium text-gray-500") { +"Variant" }
                  div("mt-1 text-sm text-gray-900") {
                    +serviceDetails.variant
                  }
                }

                div {
                  span("text-sm font-medium text-gray-500") { +"Connector" }
                  div("mt-1 text-sm text-gray-900 font-mono bg-gray-100 px-2 py-1 rounded") {
                    +serviceDetails.connector
                  }
                }

                div {
                  span("text-sm font-medium text-gray-500") { +"Connector Extra Data" }
                  div("mt-1") {
                    if (serviceDetails.connector_extra_data.isNullOrBlank()) {
                      div("text-sm text-gray-500 italic") { +"None" }
                    } else {
                      pre("text-sm text-gray-900 bg-gray-50 p-3 rounded border overflow-x-auto whitespace-pre-wrap") {
                        +serviceDetails.connector_extra_data
                      }
                    }
                  }
                }

                div {
                  span("text-sm font-medium text-gray-500") { +"Slack Channel" }
                  div("mt-1 text-sm text-gray-900") {
                    if (serviceDetails.slack_channel.isNullOrBlank()) {
                      span("text-gray-500 italic") { +"None configured" }
                    } else {
                      span("font-mono bg-blue-100 text-blue-800 px-2 py-1 rounded") {
                        +serviceDetails.slack_channel
                      }
                    }
                  }
                }

                // Timestamps section
                div("border-t border-gray-200 pt-6") {
                  span("text-sm font-medium text-gray-500 block mb-4") { +"Timestamps" }

                  div("grid grid-cols-1 md:grid-cols-3 gap-4") {
                    div {
                      span("text-xs font-medium text-gray-500") { +"Created At" }
                      div("mt-1 text-sm text-gray-900") {
                        +serviceDetails.created_at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                      }
                    }

                    div {
                      span("text-xs font-medium text-gray-500") { +"Updated At" }
                      div("mt-1 text-sm text-gray-900") {
                        +serviceDetails.updated_at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                      }
                    }

                    div {
                      span("text-xs font-medium text-gray-500") { +"Last Registered At" }
                      div("mt-1 text-sm text-gray-900") {
                        if (serviceDetails.last_registered_at != null) {
                          +serviceDetails.last_registered_at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        } else {
                          span("text-red-600 font-medium") { +"Never registered" }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }

      return Response(htmlResponseBody)
    } catch (e: Exception) {
      val htmlResponseBody = dashboardPageLayout.newBuilder()
        .title("Service Info Error | Backfila")
        .breadcrumbLinks(
          Link("Services", ServiceIndexAction.PATH),
        )
        .buildHtmlResponseBody {
          PageTitle("Service Information", "Error")

          div("bg-red-50 border border-red-200 rounded-md p-4") {
            div("flex") {
              div("ml-3") {
                span("text-sm font-medium text-red-800") { +"Error loading service information" }
                div("mt-2 text-sm text-red-700") {
                  +e.message.orEmpty()
                }
              }
            }
          }
        }

      return Response(htmlResponseBody)
    }
  }

  companion object {
    private const val PATH = "/services/{service}/{variantOrBlank}/info"
    fun path(service: String, variantOrBlank: String?) = PATH
      .replace("{service}", service)
      .replace("{variantOrBlank}", variantOrBlank ?: "")
  }
}
