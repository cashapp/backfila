package app.cash.backfila.ui.pages

import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.RunPartitionQuery
import java.net.HttpURLConnection
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import misk.exceptions.BadRequestException
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.security.authz.Authenticated
import misk.web.FormValue
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
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
) : WebAction {

  @Post("/backfills/{id}/partitions/{partitionName}/edit-cursor")
  @RequestContentType(MediaTypes.APPLICATION_FORM_URLENCODED)
  @Authenticated(capabilities = ["users"])
  fun post(
    @PathParam id: Long,
    @PathParam partitionName: String,
    @FormValue cursorSnapshot: String,
    @FormValue newCursor: String,
  ): Response<ResponseBody> {
    // Validate UTF-8
    try {
      newCursor.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8)
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
          partitionRecord.pkey_cursor = newCursor.encodeUtf8()
          session.save(partitionRecord)
        } ?: throw BadRequestException("Partition not found")
    }

    return Response(
      body = "".toResponseBody(),
      headers = Headers.Builder()
        .add("Location", BackfillShowAction.path(id))
        .build(),
      statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
    )
  }

  companion object {
    fun path(id: Long, partitionName: String): String {
      return "/backfills/$id/partitions/${URLEncoder.encode(partitionName, "UTF-8")}/edit-cursor"
    }
  }
}
