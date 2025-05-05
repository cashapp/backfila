package app.cash.backfila.ui.actions

import app.cash.backfila.dashboard.CancelBackfillAction
import app.cash.backfila.dashboard.SoftDeleteBackfillAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.dashboard.StopBackfillRequest
import app.cash.backfila.dashboard.UpdateBackfillAction
import app.cash.backfila.dashboard.UpdateBackfillRequest
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.DashboardPageLayout
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.div
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
import wisp.logging.getLogger

@Singleton
class BackfillShowButtonHandlerAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
  private val startBackfillAction: StartBackfillAction,
  private val stopBackfillAction: StopBackfillAction,
  private val updateBackfillAction: UpdateBackfillAction,
  private val cancelBackfillAction: CancelBackfillAction,
  private val softDeleteBackfillAction: SoftDeleteBackfillAction,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam id: String,
    @QueryParam field_id: String?,
    @QueryParam field_value: String?,
  ): Response<ResponseBody> {
    try {
      if (!field_id.isNullOrBlank()) {
        when (field_id) {
          "state" -> {
            when (field_value) {
              BackfillState.PAUSED.name -> {
                stopBackfillAction.stop(id.toLong(), StopBackfillRequest())
              }
              BackfillState.RUNNING.name -> {
                startBackfillAction.start(id.toLong(), StartBackfillRequest())
              }
              BackfillState.CANCELLED.name -> {
                cancelBackfillAction.cancel(id.toLong())
              }
              "soft_delete" -> {
                softDeleteBackfillAction.softDelete(id.toLong())
              }
            }
          }

          "num_threads" -> {
            val numThreads = field_value?.toIntOrNull()
            if (numThreads != null) {
              updateBackfillAction.update(id.toLong(), UpdateBackfillRequest(num_threads = numThreads))
            }
          }

          "scan_size" -> {
            val scanSize = field_value?.toLongOrNull()
            if (scanSize != null) {
              updateBackfillAction.update(id.toLong(), UpdateBackfillRequest(scan_size = scanSize))
            }
          }

          "batch_size" -> {
            val batchSize = field_value?.toLongOrNull()
            if (batchSize != null) {
              updateBackfillAction.update(id.toLong(), UpdateBackfillRequest(batch_size = batchSize))
            }
          }

          "extra_sleep_ms" -> {
            val extraSleepMs = field_value?.toLongOrNull()
            if (extraSleepMs != null) {
              updateBackfillAction.update(id.toLong(), UpdateBackfillRequest(extra_sleep_ms = extraSleepMs))
            }
          }

          "backoff_schedule" -> {
            updateBackfillAction.update(id.toLong(), UpdateBackfillRequest(backoff_schedule = field_value))
          }
        }
      }
    } catch (e: Exception) {
      // Since this action is only hit from the UI, catch any validation errors and show them to the user
      val errorHtmlResponseBody = dashboardPageLayout.newBuilder()
        .buildHtmlResponseBody {
          div("py-20") {
            AlertError(message = "Update backfill field failed: $e", label = "Try Again", onClick = "history.back(); return false;")
          }
        }
      logger.error(e) { "Update backfill field failed $e" }
      return Response(
        body = errorHtmlResponseBody,
        statusCode = 200,
        headers = Headers.headersOf("Content-Type", MediaTypes.TEXT_HTML),
      )
    }

    return Response(
      body = "go to /backfills/$id".toResponseBody(),
      statusCode = 303,
      headers = Headers.headersOf("Location", "/backfills/$id"),
    )
  }

  companion object {
    private val logger = getLogger<BackfillShowButtonHandlerAction>()

    const val PATH = "/api/backfill/{id}/update"
    fun path(id: String) = PATH.replace("{id}", id)
    fun path(id: Long) = path(id.toString())
  }
}
