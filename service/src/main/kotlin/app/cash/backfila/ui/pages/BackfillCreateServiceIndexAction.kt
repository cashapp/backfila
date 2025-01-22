package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetRegisteredBackfillsAction
import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.li
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.ul
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
  private val backfillCreateAction: BackfillCreateAction,
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
      val newPath = BackfillCreateAction.PATH
        .replace("{service}", service)
        .replace("{variantOrBackfillNameOrId}", variantOrBlank.orEmpty())
        .replace("{backfillNameOrId}", "")
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
          ServiceShowAction.PATH
            .replace("{service}", service)
            .replace("{variantOrBlank}", variantOrBlank ?: ""),
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
          // If service + variant is set and valid, show registered backfills drop down
          div("py-10") {
            ul("grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3") {
              role = "list"

              registeredBackfills.backfills.map {
                a {
                  val variantOrBackfillNameOrId = variantOrBlank.orEmpty().ifBlank { it.name }
                  href = BackfillCreateAction.PATH
                    .replace("{service}", service)
                    .replace("{variantOrBackfillNameOrId}", variantOrBackfillNameOrId)
                    .replace("{backfillNameOrId}", if (variantOrBackfillNameOrId == it.name) "" else it.name)

                  // TODO make full width
                  this@ul.li("registration col-span-1 divide-y divide-gray-200 rounded-lg bg-white shadow") {
                    div("flex w-full items-center justify-between space-x-6 p-6") {
                      div("flex-1 truncate") {
                        div("flex items-center space-x-3") {
                          // Don't include default variant in label, only for unique variants
//                          val label = if (variant == "default") service else "$service/$variant"
                          h3("truncate text-sm font-medium text-gray-900") {
                            +it.name
                          }
//                          variant?.let { span("inline-flex shrink-0 items-center rounded-full bg-green-50 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20") { +it } }
                        }
                        //                    p("mt-1 truncate text-sm text-gray-500") { +"""Regional Paradigm Technician""" }
                      }
                    }
                  }
                  // Buttons
//                div {
//                  div("-mt-px flex divide-x divide-gray-200") {
//                    div("flex w-0 flex-1") {
//                      a(classes = "relative -mr-px inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-bl-lg border border-transparent py-4 text-sm font-semibold text-gray-900") {
//                        href = "mailto:janecooper@example.com"
// //                        svg("size-5 text-gray-400") {
// //                          viewbox = "0 0 20 20"
// //                          fill = "currentColor"
// //                          attributes["aria-hidden"] = "true"
// //                          attributes["data-slot"] = "icon"
// //                          path {
// //                            d =
// //                              "M3 4a2 2 0 0 0-2 2v1.161l8.441 4.221a1.25 1.25 0 0 0 1.118 0L19 7.162V6a2 2 0 0 0-2-2H3Z"
// //                          }
// //                          path {
// //                            d =
// //                              "m19 8.839-7.77 3.885a2.75 2.75 0 0 1-2.46 0L1 8.839V14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8.839Z"
// //                          }
// //                        }
//                        +"""Email"""
//                      }
//                    }
//                    div("-ml-px flex w-0 flex-1") {
//                      a(classes = "relative inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-br-lg border border-transparent py-4 text-sm font-semibold text-gray-900") {
//                        href = "tel:+1-202-555-0170"
// //                        svg("size-5 text-gray-400") {
// //                          viewbox = "0 0 20 20"
// //                          fill = "currentColor"
// //                          attributes["aria-hidden"] = "true"
// //                          attributes["data-slot"] = "icon"
// //                          path {
// //                            attributes["fill-rule"] = "evenodd"
// //                            d =
// //                              "M2 3.5A1.5 1.5 0 0 1 3.5 2h1.148a1.5 1.5 0 0 1 1.465 1.175l.716 3.223a1.5 1.5 0 0 1-1.052 1.767l-.933.267c-.41.117-.643.555-.48.95a11.542 11.542 0 0 0 6.254 6.254c.395.163.833-.07.95-.48l.267-.933a1.5 1.5 0 0 1 1.767-1.052l3.223.716A1.5 1.5 0 0 1 18 15.352V16.5a1.5 1.5 0 0 1-1.5 1.5H15c-1.149 0-2.263-.15-3.326-.43A13.022 13.022 0 0 1 2.43 8.326 13.019 13.019 0 0 1 2 5V3.5Z"
// //                            attributes["clip-rule"] = "evenodd"
// //                          }
// //                        }
//                        +"""Call"""
//                      }
//                    }
//                  }
//                }
                }
              }
            }
          }
        }
      }

    return Response(htmlResponseBody)
  }

  enum class BackfillCreateField(val fieldId: String) {
    SERVICE("service"),
    VARIANT("variant"),
    BACKFILL_NAME("backfillName"),
    DRY_RUN("dryRun"),
    RANGE_START("rangeStart"),
    RANGE_END("rangeEnd"),
    BATCH_SIZE("batchSize"),
    SCAN_SIZE("scanSize"),
    THREADS_PER_PARTITION("threadsPerPartition"),
    EXTRA_SLEEP_MS("extraSleepMs"),
    BACKOFF_SCHEDULE("backoffSchedule"),
    CUSTOM_PARAMETER_PREFIX("customParameter_"),
  }

  companion object {
    const val PATH = "/backfills/create/{service}/{variantOrBlank}"
  }
}
