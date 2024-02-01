package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.GetBackfillStatusResponse
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.AlertSupport
import app.cash.backfila.ui.components.DashboardLayout
import app.cash.backfila.ui.components.PageTitle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.ButtonType
import kotlinx.html.button
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.span
import misk.hotwire.buildHtmlResponseBody
import misk.security.authz.Unauthenticated
import misk.tailwind.Link
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

        // TODO add Header buttons / metrics

        div("mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8") {
          div("mx-auto grid max-w-2xl grid-cols-1 grid-rows-1 items-start gap-x-8 gap-y-8 lg:mx-0 lg:max-w-none lg:grid-cols-3") {
//            +"""<!-- Right Small Column -->"""
            div("lg:col-start-3 lg:row-end-1") {
              div("rounded-lg bg-gray-50 shadow-sm ring-1 ring-gray-900/5 p-6") {
                h2("text-base font-semibold leading-6 text-gray-900") { +"""Configuration""" }
                dl("divide-y divide-gray-100") {
                  backfill.toRows().map {
                    div("px-4 py-6 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-0") {
                      this@dl.dt("text-sm font-medium leading-6 text-gray-900") { +it.label }
                      this@dl.dd("mt-1 flex text-sm leading-6 text-gray-700 sm:col-span-2 sm:mt-0") {
                        span("flex-grow") { +it.description }
                        span("ml-4 flex-shrink-0") {
                          it.button?.let { button ->
                            button(classes = "rounded-md bg-white font-medium text-indigo-600 hover:text-indigo-500") {
                              type = ButtonType.button
                              +button.label
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }

//            +"""<!-- Left Main Column -->"""
            div("-mx-4 px-4 py-8 shadow-sm ring-1 ring-gray-900/5 sm:mx-0 sm:rounded-lg sm:px-8 sm:pb-14 lg:col-span-2 lg:row-span-2 lg:row-end-2 xl:px-16 xl:pb-20 xl:pt-16") {
              h2("text-base font-semibold leading-6 text-gray-900") { +"""Partitions""" }
              dl("divide-y divide-gray-100") {
                h1 { +"""Rows""" }
              }
            }
          }
        }

        AlertSupport(config.support_button_label, config.support_button_url)
      }
    }

    return Response(htmlResponseBody)
  }

  data class DescriptionListRow(
    val label: String,
    val description: String,
    val button: Link? = null,
  )

  private fun GetBackfillStatusResponse.toRows() = listOf(
    DescriptionListRow(
      label = "State",
      description = state.name,
      button = Link(
        label = if (state == BackfillState.PAUSED) "Start" else "Pause",
        href = "#",
      ),
    ),
    DescriptionListRow(
      label = "Dry Run",
      description = if (dry_run) "dry run" else "wet run",
    ),
    DescriptionListRow(
      label = "Threads per partition",
      description = num_threads.toString(),
      button = Link(
        label = "Update",
        href = "#",
      ),
    ),
    DescriptionListRow(
      label = "Scan Size",
      description = scan_size.toString(),
      button = Link(
        label = "Update",
        href = "#",
      ),
    ),
    DescriptionListRow(
      label = "Batch Size",
      description = batch_size.toString(),
      button = Link(
        label = "Update",
        href = "#",
      ),
    ),
    DescriptionListRow(
      label = "Sleep betweeen batches (ms)",
      description = extra_sleep_ms.toString(),
      button = Link(
        label = "Update",
        href = "#",
      ),
    ),
    DescriptionListRow(
      label = "Backoff Schedule",
      description = backoff_schedule ?: "",
      button = Link(
        label = "Update",
        href = "#",
      ),
    ),
    DescriptionListRow(
      label = "Created",
      description = "$created_at by $created_by_user",
    ),
    DescriptionListRow(
      label = "Logs",
      description = "",
      button = Link(
        label = "View",
        href = "#",
      ),
    ),
  )

  companion object {
    const val PATH = "/backfills/{id}"
  }
}
