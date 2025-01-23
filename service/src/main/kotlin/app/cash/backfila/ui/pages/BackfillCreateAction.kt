package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.GetRegisteredBackfillsAction
import app.cash.backfila.ui.actions.BackfillCreateHandlerAction
import app.cash.backfila.ui.actions.ServiceDataHelper
import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.p
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackfillCreateAction @Inject constructor(
  private val serviceDataHelper: ServiceDataHelper,
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
    @PathParam variantOrBackfillNameOrId: String,
    @PathParam backfillNameOrId: String? = "",
  ): Response<ResponseBody> {
    val variantOrBlank = if (backfillNameOrId.isNullOrBlank()) {
      // This means variant is null or default
      null
    } else {
      variantOrBackfillNameOrId
    }

    val variant = variantOrBlank.orEmpty().ifBlank { "default" }
    val label = if (variant == "default") service else "$service ($variant)"
    val backfillName: String? =
      listOf(variantOrBackfillNameOrId, backfillNameOrId).firstOrNull { it?.contains(".") == true }
    val backfillIdToClone: String? =
      listOf(variantOrBackfillNameOrId, backfillNameOrId).firstOrNull { it?.toIntOrNull() != null }

    // If service + variant + backfill id to clone are valid, pre-fill form with backfill details
    val backfillRuns = getBackfillRunsAction.backfillRuns(service, variant)
    val backfillToClone =
      (backfillRuns.paused_backfills + backfillRuns.running_backfills).firstOrNull { it.id == backfillIdToClone }
    val backfillToCloneStatus = backfillToClone?.id?.toLongOrNull()?.let { getBackfillStatusAction.status(it) }

    val registeredBackfills = getRegisteredBackfillsAction.backfills(service, variant)
    val registeredBackfill =
      registeredBackfills.backfills.firstOrNull { it.name == backfillName || it.name == backfillToClone?.name.orEmpty() }

    val resolvedBackfillName = registeredBackfill?.name ?: backfillToClone?.name ?: ""

    val cloneOrCreate = if (backfillToClone != null) {
      "Clone"
    } else {
      "Create"
    }

    val htmlResponseBody = dashboardPageLayout.newBuilder()
      .title("$cloneOrCreate Backfill | Backfila")
      .breadcrumbLinks(
        Link("Services", ServiceIndexAction.PATH),
        Link(
          label,
          ServiceShowAction.path(service = service, variantOrBlank = if (variant != "default") variant else ""),
        ),
        if (backfillToClone != null) {
          Link("Backfill #${backfillToClone.id}", BackfillShowAction.path(backfillToClone.id))
        } else if (registeredBackfill != null) {
          Link("Create", BackfillCreateServiceIndexAction.path(service = service, variantOrBlank = variantOrBlank))
        } else {
          null
        },
        if ((backfillToClone?.id ?: registeredBackfill?.name) != null) {
          Link(
            backfillToClone?.id?.let { "Clone" } ?: registeredBackfill?.name ?: "",
            PATH
              .replace("{service}", service)
              .replace("{variantOrBackfillNameOrId}", variantOrBackfillNameOrId)
              .replace("{backfillNameOrId}", backfillNameOrId ?: ""),
          )
        } else {
          null
        },
      )
      .buildHtmlResponseBody {
        PageTitle("$cloneOrCreate Backfill", backfillToClone?.id, smallerSubtitle = registeredBackfill?.name) {
          span("inline-flex shrink-0 items-center rounded-full bg-blue-50 px-2.5 py-0.5 text-s font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20") {
            val suffix = if (variantOrBlank.isNullOrBlank()) "" else "/$variantOrBlank"
            +"$service$suffix"
          }
        }

        if (registeredBackfill == null && backfillToClone == null) {
          AlertError(
            message = "Invalid backfill name to create or ID to clone provided.",
            label = "Create a Backfill",
            link = BackfillCreateServiceIndexAction.path(service = service, variantOrBlank = variantOrBlank ?: ""),
          )
        } else {
          div("mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8") {
            form {
              action = BackfillCreateHandlerAction.PATH

              input {
                type = InputType.hidden
                name = BackfillCreateField.SERVICE.fieldId
                value = service
              }

              input {
                type = InputType.hidden
                name = BackfillCreateField.VARIANT.fieldId
                value = variant
              }

              input {
                type = InputType.hidden
                name = BackfillCreateField.BACKFILL_NAME.fieldId
                value = resolvedBackfillName
              }

              div("space-y-12") {
                div("border-b border-gray-900/10 pb-12") {
                  h2("text-base/7 font-semibold text-gray-900") { +"""Immutable Options""" }
                  p("mt-1 text-sm/6 text-gray-600") { +"""These options can't be changed once the backfill is created.""" }
                  div {
                    div("mt-6 space-y-6") {
                      div("flex gap-3") {
                        val field = BackfillCreateField.DRY_RUN.fieldId
                        div("flex h-6 shrink-0 items-center") {
                          div("group grid size-4 grid-cols-1") {
                            input(classes = "col-start-1 row-start-1 appearance-none rounded border border-gray-300 bg-white checked:border-indigo-600 checked:bg-indigo-600 indeterminate:border-indigo-600 indeterminate:bg-indigo-600 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:border-gray-300 disabled:bg-gray-100 disabled:checked:bg-gray-100 forced-colors:appearance-auto") {
                              id = field
                              attributes["aria-describedby"] = "dry-run-description"
                              name = field
                              type = InputType.checkBox
                              // Checked by default to force user to opt-in to non-dry-run
                              checked = true
                              placeholder = "on"
                              backfillToCloneStatus?.let {
                                if (it.dry_run) {
                                  value = "on"
                                  checked = true
                                } else {
                                  value = "off"
                                  checked = false
                                }
                              }
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
                      val field = BackfillCreateField.RANGE_START.fieldId
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
                          // TODO is this how to clone?
                          backfillToCloneStatus?.partitions?.firstOrNull()?.pkey_start?.let { value = it }
                        }
                      }
                    }
                    div("sm:col-span-3") {
                      val field = BackfillCreateField.RANGE_END.fieldId
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
                          // TODO is this how to clone?
                          backfillToCloneStatus?.partitions?.firstOrNull()?.pkey_end?.let { value = it }
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
                      val field = BackfillCreateField.BATCH_SIZE.fieldId
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
                          backfillToCloneStatus?.batch_size?.let { value = it.toString() }
                        }
                      }
                    }
                  }

                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-3") {
                      val field = BackfillCreateField.SCAN_SIZE.fieldId
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
                          backfillToCloneStatus?.scan_size?.let { value = it.toString() }
                        }
                      }
                    }
                  }

                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-3") {
                      val field = BackfillCreateField.THREADS_PER_PARTITION.fieldId
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
                          backfillToCloneStatus?.num_threads?.let { value = it.toString() }
                        }
                      }
                    }
                  }

                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-3") {
                      val field = BackfillCreateField.EXTRA_SLEEP_MS.fieldId
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
                          backfillToCloneStatus?.extra_sleep_ms?.let { value = it.toString() }
                        }
                      }
                    }
                  }

                  div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                    div("sm:col-span-3") {
                      val field = BackfillCreateField.BACKOFF_SCHEDULE.fieldId
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
                          backfillToCloneStatus?.backoff_schedule?.let { value = it }
                        }
                      }
                    }
                  }
                }
              }

              // Custom Parameters
              if (registeredBackfill?.parameterNames?.isNotEmpty() == true) {
                div("pt-12 space-y-12") {
                  div("border-b border-gray-900/10 pb-12") {
                    h2("text-base/7 font-semibold text-gray-900") { +"""Immutable Custom Parameters""" }
                    p("mt-1 text-sm/6 text-gray-600") { +"""These custom parameters can't be changed once the backfill is created.""" }

                    registeredBackfill.parameterNames.map {
                      div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                        div("sm:col-span-3") {
                          val field = "${BackfillCreateField.CUSTOM_PARAMETER_PREFIX.fieldId}$it"
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
                              backfillToCloneStatus?.parameters?.get(it)?.let { value = it }
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

                +cloneOrCreate
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
    private const val PATH = "/backfills/create/{service}/{variantOrBackfillNameOrId}/{backfillNameOrId}"
    fun path(service: String, variantOrBackfillNameOrId: String, backfillNameOrId: String) = PATH
      .replace("{service}", service)
      .replace("{variantOrBackfillNameOrId}", variantOrBackfillNameOrId)
      .replace("{backfillNameOrId}", backfillNameOrId)
  }
}
