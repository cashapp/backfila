package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.GetRegisteredBackfillsAction
import app.cash.backfila.ui.actions.BackfillCreateHandlerAction
import app.cash.backfila.ui.actions.ServiceAutocompleteAction
import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.ul
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

@Singleton
class BackfillCreateAction @Inject constructor(
  private val serviceAutocompleteAction: ServiceAutocompleteAction,
  private val getBackfillStatusAction: GetBackfillStatusAction,
  private val getRegisteredBackfillsAction: GetRegisteredBackfillsAction,
  private val getBackfillRunsAction: GetBackfillRunsAction,
  private val dashboardPageLayout: DashboardPageLayout,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam service: String,
    @PathParam variantOrBlank: String? = "",
    @QueryParam backfillName: String? = "",
    @QueryParam backfillIdToClone: String? = null,
  ): Response<ResponseBody> {
    val htmlResponseBody = dashboardPageLayout.newBuilder()
      .title("Create Backfill | Backfila")
      .buildHtmlResponseBody {
        PageTitle("Create Backfill", backfillName ?: "") {
          span("inline-flex shrink-0 items-center rounded-full bg-blue-50 px-2.5 py-0.5 text-s font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20") {
            val suffix = if (variantOrBlank.isNullOrBlank()) "" else "/$variantOrBlank"
            +"$service$suffix"
          }
        }

        val variant = variantOrBlank.orEmpty().ifBlank { "default" }
        val registeredBackfills = getRegisteredBackfillsAction.backfills(service, variant)
        val backfill = registeredBackfills.backfills.firstOrNull { it.name == backfillName }

        if (service.isNotBlank() && backfill == null && backfillIdToClone == null) {
          // If service + variant is set and valid, show registered backfills drop down
          p {
            +"Service: $service"
          }
          p {
            +"Variant: $variant"
          }
          div("py-10") {
            ul("grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3") {
              role = "list"

              registeredBackfills.backfills.map {
                a {
                  // TODO redirect to same page but with backfill filled in
                  href = PATH.replace("{service}", service)
                    .replace("{variantOrBlank}", variantOrBlank ?: "") + "?backfillName=${it.name}"

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
        } else if (backfill != null || backfillIdToClone != null) {
          // If service + variant + backfill id to clone are valid, pre-fill form with backfill details
          val backfillRuns = getBackfillRunsAction.backfillRuns(service, variant)
          val backfillToClone =
            (backfillRuns.paused_backfills + backfillRuns.running_backfills).firstOrNull { it.id == backfillIdToClone }
          if (backfill == null && backfillToClone == null) {
            AlertError(
              message = "Backfill ID $backfillIdToClone to clone not found.",
              label = "Choose a Backfill",
              link = PATH.replace("{service}", service).replace("{variantOrBlank}", variantOrBlank ?: ""),
            )
          }

          // TODO add Header buttons / metrics

          // TODO add backfill name and back button to select a different backfill, or select/options

          div("mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8") {
            form {
              action = BackfillCreateHandlerAction.PATH

              input {
                type = InputType.hidden
                name = "service"
                value = service
              }

              input {
                type = InputType.hidden
                name = "variant"
                value = variant
              }

              input {
                type = InputType.hidden
                name = "backfillName"
                value = backfill?.name ?: backfillToClone?.name ?: ""
              }

              div("space-y-12") {
                div("border-b border-gray-900/10 pb-12") {
                  h2("text-base/7 font-semibold text-gray-900") { +"""Immutable Options""" }
                  p("mt-1 text-sm/6 text-gray-600") { +"""These options can't be changed once the backfill is created.""" }
                  div {
                    div("mt-6 space-y-6") {
                      div("flex gap-3") {
                        val field = "dryRun"
                        div("flex h-6 shrink-0 items-center") {
                          div("group grid size-4 grid-cols-1") {
                            input(classes = "col-start-1 row-start-1 appearance-none rounded border border-gray-300 bg-white checked:border-indigo-600 checked:bg-indigo-600 indeterminate:border-indigo-600 indeterminate:bg-indigo-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:border-gray-300 disabled:bg-gray-100 disabled:checked:bg-gray-100 forced-colors:appearance-auto") {
                              id = field
                              attributes["aria-describedby"] = "dry-run-description"
                              name = field
                              type = InputType.checkBox
                            }
                          }
                        }
                        div("text-sm/6") {
                          label("font-medium text-gray-900") {
                            htmlFor = field
                            +"""Dry Run"""
                          }
                          p("text-gray-500") {
                            id = "dry-run-description"
                            +"""Anything within your not Dry Run block will not be run."""
                          }
                        }
                      }
                    }
                  }

                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-3") {
                      val field = "rangeStart"
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = field
                        +"""Range Start (optional)"""
                      }
                      div("mt-2") {
                        input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                          type = InputType.number
                          name = field
                          id = field
                          attributes["autocomplete"] = field
                        }
                      }
                    }
                    div("sm:col-span-3") {
                      val field = "rangeEnd"
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = field
                        +"""Range End (optional)"""
                      }
                      div("mt-2") {
                        input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                          type = InputType.number
                          name = field
                          id = field
                          attributes["autocomplete"] = field
                        }
                      }
                    }
                  }
                }
              }

              div("pt-12 space-y-12") {
                div("border-b border-gray-900/10 pb-12") {
                  h2("text-base/7 font-semibold text-gray-900") { +"""Mutable Options""" }
                  p("mt-1 text-sm/6 text-gray-600") { +"""These options can be changed once the backfill is created.""" }

                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-3") {
                      val field = "batchSize"
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = field
                        +"""Batch Size"""

                        legend("text-sm/6 font-normal text-gray-900") { +"""How many *matching* records to send per call to RunBatch.""" }
                      }
                      div("mt-2") {
                        input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                          type = InputType.text
                          name = field
                          id = field
                          attributes["autocomplete"] = field
                          value = "100"
                        }
                      }
                    }
                  }

                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-3") {
                      val field = "scanSize"
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = field
                        +"""Scan Size"""

                        legend("text-sm/6 font-normal text-gray-900") { +"""How many records to scan when computing batches.""" }
                      }
                      div("mt-2") {
                        input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                          type = InputType.number
                          name = field
                          id = field
                          attributes["autocomplete"] = field
                          value = "10000"
                        }
                      }
                    }
                  }

                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-3") {
                      val field = "threadsPerPartition"
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = field
                        +"""Threads Per Partition"""
                      }
                      div("mt-2") {
                        input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                          type = InputType.number
                          name = field
                          id = field
                          attributes["autocomplete"] = field
                          value = "1"
                        }
                      }
                    }
                  }

                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-3") {
                      val field = "extraSleepMs"
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = field
                        +"""Extra Sleep (ms)"""
                      }
                      div("mt-2") {
                        input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                          type = InputType.number
                          name = field
                          id = field
                          attributes["autocomplete"] = field
                          value = "1"
                        }
                      }
                    }
                  }

                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-3") {
                      val field = "backoffSchedule"
                      label("block text-sm/6 font-medium text-gray-900") {
                        htmlFor = field
                        +"""Backoff Schedule (optional)"""

                        legend("text-sm/6 font-normal text-gray-900") { +"""Comma separated list of milliseconds to backoff subsequent failures.""" }
                      }
                      div("mt-2") {
                        input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                          type = InputType.text
                          name = field
                          id = field
                          attributes["autocomplete"] = field
                          placeholder = "5000,15000,30000"
                        }
                      }
                    }
                  }
                }
              }

              // Custom Parameters
              if (backfill?.parameterNames?.isNotEmpty() == true) {
                div("pt-12 space-y-12") {
                  div("border-b border-gray-900/10 pb-12") {
                    h2("text-base/7 font-semibold text-gray-900") { +"""Immutable Custom Parameters""" }
                    p("mt-1 text-sm/6 text-gray-600") { +"""These custom parameters can't be changed once the backfill is created.""" }

                    backfill.parameterNames.map {
                      div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                        div("sm:col-span-3") {
                          val field = "customParameter_$it"
                          label("block text-sm/6 font-medium text-gray-900") {
                            htmlFor = field
                            +it
                          }
                          div("mt-2") {
                            input(classes = "block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6") {
                              type = InputType.text
                              name = field
                              id = field
                              attributes["autocomplete"] = field
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }

              button(classes = "rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600") {
                type = ButtonType.submit

                if (backfillToClone != null) {
                  +"Clone"
                } else {
                  +"""Create"""
                }
              }
            }
          }
        }
      }

    return Response(htmlResponseBody)
  }

  companion object {
    const val PATH = "/backfills/create/{service}/{variantOrBlank}"
  }
}
