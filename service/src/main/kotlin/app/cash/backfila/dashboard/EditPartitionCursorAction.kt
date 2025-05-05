package app.cash.backfila.dashboard

import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.pages.BackfillShowAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.ButtonType
import kotlinx.html.FormMethod
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.p
import misk.security.authz.Authenticated
import misk.tailwind.Link
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

@Singleton
class EditPartitionCursorAction @Inject constructor(
  private val getBackfillStatusAction: GetBackfillStatusAction,
  private val dashboardPageLayout: DashboardPageLayout,
) : WebAction {

  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam id: Long,
    @PathParam partitionName: String,
  ): Response<ResponseBody> {
    val backfill = getBackfillStatusAction.status(id)

    val partition = backfill.partitions.find { it.name == partitionName }
      ?: throw IllegalArgumentException("Partition not found")

    // Take a snapshot of current cursor for validation
    val cursorSnapshot = partition.pkey_cursor

    return Response(
      dashboardPageLayout.newBuilder()
        .title("Edit Cursor - Partition $partitionName")
        .breadcrumbLinks(
          Link("Backfill #$id", BackfillShowAction.path(id)),
          Link("Edit Cursor", path(id, partitionName)),
        )
        .buildHtmlResponseBody {
          div("space-y-6 max-w-2xl mx-auto py-8") {
            h1("text-xl font-semibold") {
              +"Edit Cursor for Partition: $partitionName"
            }

            div("rounded-md bg-yellow-50 p-4 mb-6") {
              div("flex") {
                div("flex-shrink-0") {
                  // Warning icon
                  div("h-5 w-5 text-yellow-400") {
                    +"⚠️"
                  }
                }
                div("ml-3") {
                  h1("text-sm font-medium text-yellow-800") {
                    +"Warning: Editing cursors can be dangerous"
                  }
                  div("mt-2 text-sm text-yellow-700") {
                    p {
                      +"Make sure you understand the implications of changing the cursor position. Records between the old and new cursor positions may be skipped or processed multiple times."
                    }
                  }
                }
              }
            }

            form {
              method = FormMethod.get
              action = EditPartitionCursorHandlerAction.path(id, partitionName)

              input {
                type = InputType.hidden
                name = "cursor_snapshot"
                value = cursorSnapshot ?: ""
              }

              div("space-y-4") {
                div {
                  label("block text-sm font-medium text-gray-700") {
                    htmlFor = "current_cursor"
                    +"Current Cursor"
                  }
                  div("mt-1") {
                    input(classes = "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6") {
                      type = InputType.text
                      attributes["id"] = "current_cursor"
                      value = cursorSnapshot ?: "Not started"
                      disabled = true
                    }
                  }
                }

                div {
                  label("block text-sm font-medium text-gray-700") {
                    htmlFor = "new_cursor"
                    +"New Cursor"
                  }
                  div("mt-1") {
                    input(classes = "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6") {
                      type = InputType.text
                      name = "new_cursor"
                      attributes["id"] = "new_cursor"
                      value = cursorSnapshot ?: ""
                      required = true
                    }
                  }
                  p("mt-2 text-sm text-gray-500") {
                    +"Enter the new cursor value. This must be a valid UTF-8 string."
                  }
                }

                div("flex justify-end gap-3") {
                  a(href = BackfillShowAction.path(id), classes = "rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50") {
                    +"Cancel"
                  }
                  button(classes = "rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600") {
                    type = ButtonType.submit
                    +"Update Cursor"
                  }
                }
              }
            }
          }
        },
    )
  }

  companion object {
    private const val PATH = "/backfills/{id}/{partitions}/{partitionName}/edit-cursor"
    fun path(id: Long, partitionName: String) = PATH
      .replace("{id}", id.toString())
      .replace("{partitionName}", partitionName)
  }
}
