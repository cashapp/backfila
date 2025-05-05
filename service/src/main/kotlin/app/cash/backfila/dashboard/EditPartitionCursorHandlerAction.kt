package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.RunPartitionQuery
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.pages.BackfillShowAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.p
import misk.exceptions.BadRequestException
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.tailwind.Link
import misk.web.Get
import misk.web.HttpCall
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okio.ByteString.Companion.encodeUtf8

@Singleton
class EditPartitionCursorHandlerAction @Inject constructor(
  private val getBackfillStatusAction: GetBackfillStatusAction,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val httpCall: ActionScoped<HttpCall>,
  private val dashboardPageLayout: DashboardPageLayout,
) : WebAction {

  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam id: Long,
    @PathParam partitionName: String,
  ): Response<ResponseBody> {
    val request = httpCall.get().asOkHttpRequest()
    val cursorSnapshot = request.url.queryParameter("cursor_snapshot")
    val newCursor = request.url.queryParameter("new_cursor")

    // Validate UTF-8
    try {
      newCursor?.toByteArray(Charsets.UTF_8)?.toString(Charsets.UTF_8)
    } catch (e: Exception) {
      throw BadRequestException("New cursor must be valid UTF-8")
    }

    // Verify backfill state and cursor hasn't changed
    val backfill = getBackfillStatusAction.status(id)
    if (backfill.state != BackfillState.PAUSED) {
      throw BadRequestException("Backfill must be paused to edit cursors")
    }

    val partition = backfill.partitions.find { it.name == partitionName }
      ?: throw BadRequestException("Partition not found")

    if (partition.pkey_cursor != cursorSnapshot) {
      throw BadRequestException("Cursor has changed since edit form was loaded")
    }

    // Update the cursor
    transacter.transaction { session ->
      queryFactory.newQuery<RunPartitionQuery>()
        .partitionId(partition.id)
        .uniqueResult(session)
        ?.let { partitionRecord ->
          partitionRecord.pkey_cursor = newCursor?.encodeUtf8()
          session.save(partitionRecord)
        } ?: throw BadRequestException("Partition not found")
    }

    // Return success page with updated form
    return Response(
      dashboardPageLayout.newBuilder()
        .title("Edit Cursor - Partition $partitionName")
        .breadcrumbLinks(
          Link("Backfill #$id", BackfillShowAction.path(id)),
          Link("Edit Cursor", path(id, partitionName)),
        )
        .buildHtmlResponseBody {
          div("space-y-6 max-w-2xl mx-auto py-8") {
            // Success message
            div("rounded-md bg-green-50 p-4 mb-6") {
              div("flex") {
                div("flex-shrink-0") {
                  // Success icon (checkmark)
                  div("h-5 w-5 text-green-400") {
                    +"✓"
                  }
                }
                div("ml-3") {
                  h1("text-sm font-medium text-green-800") {
                    +"Success"
                  }
                  div("mt-2 text-sm text-green-700") {
                    p {
                      +"Cursor has been updated successfully."
                    }
                  }
                }
              }
            }
          }
        },
    )
  }

  companion object {
    private const val PATH = "/backfills/{id}/{partitionName}/edit-cursor"
    fun path(id: Long, partitionName: String) = PATH
      .replace("{id}", id.toString())
      .replace("{partitionName}", partitionName)
  }
}
