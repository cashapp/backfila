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

  // Keep the published cursor-only entry point for callers compiled against older service versions.
  fun get(
    id: Long,
    partitionId: Long,
    cursor_snapshot: String? = null,
    new_cursor: String? = null,
  ): Response<ResponseBody> = get(id, partitionId, cursor_snapshot, new_cursor, null, null)

  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam id: Long,
    @PathParam partitionId: Long,
    @QueryParam cursor_snapshot: String? = null,
    @QueryParam new_cursor: String? = null,
    @QueryParam new_range_end: String? = null,
    @QueryParam range_end_snapshot: String? = null,
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

    return when (compareAndSetCursor(id, partitionId, cursorSnapshot, newCursor, newRangeEnd, range_end_snapshot)) {
      CursorUpdate.UPDATED -> redirectToBackfillPage(id)
      CursorUpdate.CURSOR_NOT_UTF8 -> buildErrorResponse(
        "Partition ${partition.name} has a cursor that is not valid UTF-8, so it cannot be edited here.",
      )
      CursorUpdate.SNAPSHOT_STALE -> buildErrorResponse(
        "The cursor or range end has changed since the edit form was loaded. Reload the form and try again.",
      )
      CursorUpdate.NOT_PAUSED -> buildErrorResponse("The partition must still be paused. Reload the form and try again.")
      CursorUpdate.LEASE_ACTIVE -> buildErrorResponse(
        "Wait for the runner to release this partition before editing. If its runner crashed, resume the backfill to recover the lease, then pause it again.",
      )
      CursorUpdate.RANGE_END_NOT_EDITABLE -> buildErrorResponse(
        "Only partitions with an existing UTF-8 range end can have their end edited here.",
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

  /** Validate snapshots only for fields being edited, preserving untouched bytes as stored. */
  private fun compareAndSetCursor(
    id: Long,
    partitionId: Long,
    cursorSnapshot: String?,
    newCursor: String?,
    newRangeEnd: String?,
    rangeEndSnapshot: String?,
  ): CursorUpdate = transacter.transaction { session ->
    val partitionRecord = queryFactory.newQuery<RunPartitionQuery>()
      .backfillRunId(Id(id))
      .partitionId(Id(partitionId))
      .uniqueResult(session)
      ?: throw BadRequestException("Partition $partitionId not found in backfill $id")

    if (partitionRecord.run_state != BackfillState.PAUSED ||
      partitionRecord.backfill_run.state != BackfillState.PAUSED
    ) {
      return@transaction CursorUpdate.NOT_PAUSED
    }
    // Expiry alone does not stop a stalled runner from persisting its old cursor and counts.
    if (partitionRecord.lease_token != null) {
      return@transaction CursorUpdate.LEASE_ACTIVE
    }

    if (newCursor != null) {
      val storedCursor = partitionRecord.pkey_cursor
      if (storedCursor != null && storedCursor.utf8().encodeUtf8() != storedCursor) {
        return@transaction CursorUpdate.CURSOR_NOT_UTF8
      }
      if (storedCursor != cursorSnapshot?.encodeUtf8()) {
        return@transaction CursorUpdate.SNAPSHOT_STALE
      }
    }
    val storedEnd = partitionRecord.pkey_range_end
    if (newRangeEnd != null) {
      // Unbounded clients (including S3) do not necessarily honor an end in batch requests.
      if (storedEnd == null || storedEnd.utf8().encodeUtf8() != storedEnd) {
        return@transaction CursorUpdate.RANGE_END_NOT_EDITABLE
      }
      if (storedEnd != rangeEndSnapshot?.encodeUtf8()) {
        return@transaction CursorUpdate.SNAPSHOT_STALE
      }
    }
    newCursor?.let { partitionRecord.pkey_cursor = it.encodeUtf8() }
    if (newRangeEnd != null && newRangeEnd.encodeUtf8() != storedEnd) {
      partitionRecord.pkey_range_end = newRangeEnd.encodeUtf8()
      // Completed and partial totals both describe the previous range. Recount the edited range
      // on the next lease without changing the history of records already processed.
      partitionRecord.precomputing_done = false
      partitionRecord.precomputing_pkey_cursor = null
      partitionRecord.computed_scanned_record_count = 0
      partitionRecord.computed_matching_record_count = 0
    }
    CursorUpdate.UPDATED
  }

  private enum class CursorUpdate { UPDATED, SNAPSHOT_STALE, CURSOR_NOT_UTF8, NOT_PAUSED, LEASE_ACTIVE, RANGE_END_NOT_EDITABLE }

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
