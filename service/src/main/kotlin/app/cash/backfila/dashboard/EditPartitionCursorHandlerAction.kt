package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.RunPartitionQuery
import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.pages.BackfillShowAction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.div
import misk.exceptions.BadRequestException
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.HttpCall
import misk.web.PathParam
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
    val cursorSnapshot = request.url.queryParameter("cursor_snapshot")?.takeIf { it.isNotBlank() }
    val newCursor = request.url.queryParameter("new_cursor")

    if (!isValidUtf8(newCursor)) {
      return buildErrorResponse("New cursor must be valid UTF8. New Cursor: $newCursor")
    }

    val backfill = getBackfillStatusAction.status(id)
    if (backfill.state != BackfillState.PAUSED) {
      return buildErrorResponse("Backfill must be paused. Current State: ${backfill.state}")
    }

    val partition = backfill.partitions.find { it.name == partitionName }
      ?: return buildErrorResponse("Partition not found: $partitionName")

    if (partition.pkey_cursor != cursorSnapshot) {
      return buildErrorResponse("Cursor has changed since edit form was loaded. Current Cursor: ${partition.pkey_cursor}")
    }

    updateCursor(partition.id, newCursor)

    return redirectToBackfillPage(id)
  }

  private fun isValidUtf8(input: String?): Boolean {
    return input == null || input.toByteArray(Charsets.UTF_8).contentEquals(input.toByteArray(Charsets.UTF_8))
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

  private fun updateCursor(partitionId: Long, newCursor: String?) {
    transacter.transaction { session ->
      queryFactory.newQuery<RunPartitionQuery>()
        .partitionId(partitionId)
        .uniqueResult(session)
        ?.let { partitionRecord ->
          partitionRecord.pkey_cursor = newCursor?.encodeUtf8()
          session.save(partitionRecord)
        } ?: throw BadRequestException("Partition not found")
    }
  }

  private fun redirectToBackfillPage(id: Long): Response<ResponseBody> {
    return Response(
      body = "go to ${BackfillShowAction.path(id)}".toResponseBody(),
      statusCode = 303,
      headers = Headers.headersOf("Location", BackfillShowAction.path(id)),
    )
  }

  companion object {
    private const val PATH = "/backfills/{id}/{partitionName}/edit-cursor"

    fun path(id: Long, partitionName: String) = PATH
      .replace("{id}", id.toString())
      .replace("{partitionName}", partitionName)
  }
}
