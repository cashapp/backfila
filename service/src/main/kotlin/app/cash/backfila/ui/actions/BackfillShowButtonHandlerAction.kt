package app.cash.backfila.ui.actions

import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.dashboard.StopBackfillRequest
import app.cash.backfila.dashboard.UpdateBackfillAction
import app.cash.backfila.dashboard.UpdateBackfillRequest
import app.cash.backfila.service.persistence.BackfillState
import javax.inject.Inject
import javax.inject.Singleton
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

@Singleton
class BackfillShowButtonHandlerAction @Inject constructor(
  private val startBackfillAction: StartBackfillAction,
  private val stopBackfillAction: StopBackfillAction,
  private val updateBackfillAction: UpdateBackfillAction,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @PathParam id: String,
    @QueryParam field_id: String?,
    @QueryParam field_value: String?,
  ): Response<ResponseBody> {
    if (!field_id.isNullOrBlank()) {
      when (field_id) {
        "state" -> {
          if (field_value == BackfillState.PAUSED.name) {
            stopBackfillAction.stop(id.toLong(), StopBackfillRequest())
          } else if (field_value == BackfillState.RUNNING.name) {
            startBackfillAction.start(id.toLong(), StartBackfillRequest())
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

    return Response(
      body = "go to /backfills/$id".toResponseBody(),
      statusCode = 303,
      headers = Headers.headersOf("Location", "/backfills/$id"),
    )
  }

  companion object {
    const val PATH = "/api/backfill/{id}/update"
    fun path(id: String) = PATH.replace("{id}", id)
  }
}
