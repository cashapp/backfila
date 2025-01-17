package app.cash.backfila.ui.components

import app.cash.backfila.dashboard.GetServicesAction
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.input
import kotlinx.html.li
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.ul

/** Search and select from Services */
fun TagConsumer<*>.ServiceSelect(
  services: Map<String, GetServicesAction.UiService>,
  serviceLinkBuilder: (String, String?) -> String,
) {
  div {
    attributes["data-controller"] = "search-bar"

    // Search Bar
    div {
      input(
        type = InputType.search,
        classes = "flex h-10 w-full bg-gray-100 hover:bg-gray-200 duration-500 border-none rounded-lg text-sm",
      ) {
        attributes["data-action"] = "input->search-bar#search"
        placeholder = "Search"
      }
    }
    div("py-10") {
      ul("grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3") {
        role = "list"

        services.map { (servicePath, service) ->
          a {
            val variant = if (servicePath.split("/").last() == "default") null else servicePath.split("/").last()
            href = serviceLinkBuilder(service.name, variant)

            this@ul.li("registration col-span-1 divide-y divide-gray-200 rounded-lg bg-white shadow") {
              div("flex w-full items-center justify-between space-x-6 p-6") {
                div("flex-1 truncate") {
                  div("flex items-center space-x-3") {
                    // Don't include default variant in label, only for unique variants
                    val label = if (variant == null) service.name else servicePath
                    h3("truncate text-sm font-medium text-gray-900") {
                      +"""$label (${service.running_backfills})"""
                    }
                    variant?.let { span("inline-flex shrink-0 items-center rounded-full bg-green-50 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20") { +it } }
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
