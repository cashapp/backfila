package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.RunPartitionQuery
import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.pages.BackfillShowAction
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.div
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okio.ByteString.Companion.encodeUtf8

@Singleton
class EditPartitionCursorHandlerAction @Inject constructor(
  private val getBackfillStatusAction: GetBackfillStatusAction,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val dashboardPageLayout: DashboardPageLayout,
) : WebAction {

  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam id: Long,
    @PathParam partitionId: Long,
    @QueryParam cursor_snapshot: String? = null,
    @QueryParam new_cursor: String? = null,
    @QueryParam new_range_end: String? = null,
  ): Response<ResponseBody> {
    val cursorSnapshot = cursor_snapshot?.takeIf { it.isNotBlank() }
    val newCursor = new_cursor?.takeIf { it.isNotBlank() }
    val newRangeEnd = new_range_end?.takeIf { it.isNotBlank() }
    if (newCursor == null && newRangeEnd == null) {
      return buildErrorResponse("A new cursor or a new range end is required.")
    }

    val backfill = getBackfillStatusAction.status(id)
    if (backfill.state != BackfillState.PAUSED) {
      return buildErrorResponse("Backfill must be paused. Current State: ${backfill.state}")
    }

    val partition = backfill.partitions.find { it.id == partitionId }
      ?: return buildErrorResponse("Partition $partitionId not found in backfill $id")

    return when (compareAndSetCursor(id, partitionId, cursorSnapshot, newCursor, newRangeEnd)) {
      CursorUpdate.UPDATED -> redirectToBackfillPage(id)
      CursorUpdate.CURSOR_NOT_UTF8 -> buildErrorResponse(
        "Partition ${partition.name} has a cursor that is not valid UTF-8, so it cannot be edited here.",
      )
      CursorUpdate.SNAPSHOT_STALE -> buildErrorResponse(
        "Cursor has changed since edit form was loaded. Current Cursor: ${partition.pkey_cursor}",
      )
    }
  }

  private fun buildErrorResponse(message: String): Response<ResponseBody> {
    val errorHtmlResponseBody = dashboardPageLayout.newBuilder()
      .buildHtmlResponseBody {
        div("py-20") {
          AlertError(message = "Edit partition failed. $message", label = "Try Again", onClick = "history.back(); return false;")
        }
      }
    return Response(
      body = errorHtmlResponseBody,
      statusCode = 200,
      headers = Headers.headersOf("Content-Type", MediaTypes.TEXT_HTML),
    )
  }

  /** The form round-trips the snapshot through `utf8()`, so bytes that are not UTF-8 cannot be compared. */
  private fun compareAndSetCursor(
    id: Long,
    partitionId: Long,
    cursorSnapshot: String?,
    newCursor: String?,
    newRangeEnd: String?,
  ): CursorUpdate = transacter.transaction { session ->
    val partitionRecord = queryFactory.newQuery<RunPartitionQuery>()
      .backfillRunId(Id(id))
      .partitionId(Id(partitionId))
      .uniqueResult(session)
      ?: throw BadRequestException("Partition $partitionId not found in backfill $id")

    val storedCursor = partitionRecord.pkey_cursor
    if (storedCursor != null && storedCursor.utf8().encodeUtf8() != storedCursor) {
      return@transaction CursorUpdate.CURSOR_NOT_UTF8
    }
    if (storedCursor != cursorSnapshot?.encodeUtf8()) {
      return@transaction CursorUpdate.SNAPSHOT_STALE
    }
    newCursor?.let { partitionRecord.pkey_cursor = it.encodeUtf8() }
    // The runner reloads its metadata when the partition is leased again, and both the runner and
    // the precomputer stop when the client reports no batches past the range end, so a narrowed end
    // takes effect on resume.
    newRangeEnd?.let { partitionRecord.pkey_range_end = it.encodeUtf8() }
    CursorUpdate.UPDATED
  }

  private enum class CursorUpdate { UPDATED, SNAPSHOT_STALE, CURSOR_NOT_UTF8 }

  private fun redirectToBackfillPage(id: Long): Response<ResponseBody> {
    return Response(
      body = "go to ${BackfillShowAction.path(id)}".toResponseBody(),
      statusCode = 303,
      headers = Headers.headersOf("Location", BackfillShowAction.path(id)),
    )
  }

  companion object {
    private const val PATH = "/api/backfill/{id}/partitions/{partitionId}/cursor"

    fun path(id: Long, partitionId: Long) = PATH
      .replace("{id}", id.toString())
      .replace("{partitionId}", partitionId.toString())
  }
}
