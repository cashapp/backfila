package app.cash.backfila.ui.actions

import app.cash.backfila.dashboard.CancelBackfillAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
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
import app.cash.backfila.ui.pages.BackfillShowAction.Companion.CANCEL_STATE_BUTTON_LABEL
import app.cash.backfila.ui.pages.BackfillShowAction.Companion.DELETE_STATE_BUTTON_LABEL
import app.cash.backfila.ui.pages.BackfillShowAction.Companion.PAUSE_STATE_BUTTON_LABEL
import app.cash.backfila.ui.pages.BackfillShowAction.Companion.START_STATE_BUTTON_LABEL
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.input
import kotlinx.html.span
import misk.security.authz.Authenticated
import misk.tailwind.Link
import misk.turbo.turbo_frame
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
  private val getBackfillStatusAction: GetBackfillStatusAction,
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
        handleFieldUpdate(id.toLong(), field_id, field_value)
      }
    } catch (e: Exception) {
      return handleError(e)
    }

    return when (field_id) {
      "state" -> handleStateFrameResponse(id)
      else -> handleRedirectResponse(id)
    }
  }

  private fun handleFieldUpdate(id: Long, fieldId: String, fieldValue: String?) {
    when (fieldId) {
      "state" -> handleStateUpdate(id, fieldValue)
      else -> handleConfigUpdate(id, fieldId, fieldValue)
    }
  }

  private fun handleStateUpdate(id: Long, value: String?) {
    when (value) {
      BackfillState.PAUSED.name -> stopBackfillAction.stop(id, StopBackfillRequest())
      BackfillState.RUNNING.name -> startBackfillAction.start(id, StartBackfillRequest())
      BackfillState.CANCELLED.name -> cancelBackfillAction.cancel(id)
      "soft_delete" -> softDeleteBackfillAction.softDelete(id)
    }
  }

  private fun handleConfigUpdate(id: Long, fieldId: String, value: String?) {
    val request = when (fieldId) {
      "num_threads" -> value?.toIntOrNull()?.let { UpdateBackfillRequest(num_threads = it) }
      "scan_size" -> value?.toLongOrNull()?.let { UpdateBackfillRequest(scan_size = it) }
      "batch_size" -> value?.toLongOrNull()?.let { UpdateBackfillRequest(batch_size = it) }
      "extra_sleep_ms" -> value?.toLongOrNull()?.let { UpdateBackfillRequest(extra_sleep_ms = it) }
      "backoff_schedule" -> value?.let { UpdateBackfillRequest(backoff_schedule = it) }
      else -> null
    }
    request?.let { updateBackfillAction.update(id, it) }
  }

  private fun handleError(e: Exception): Response<ResponseBody> {
    logger.error(e) { "Update backfill field failed $e" }
    val errorHtmlResponseBody = dashboardPageLayout.newBuilder()
      .buildHtmlResponseBody {
        div("py-20") {
          AlertError(message = "Update backfill field failed: $e", label = "Try Again", onClick = "history.back(); return false;")
        }
      }
    return Response(
      body = errorHtmlResponseBody,
      statusCode = 200,
      headers = Headers.headersOf("Content-Type", MediaTypes.TEXT_HTML),
    )
  }

  private fun handleStateFrameResponse(id: String): Response<ResponseBody> {
    val backfillStatus = getBackfillStatusAction.status(id.toLong())
    val currentState = backfillStatus.state
    val deletedAt = backfillStatus.deleted_at

    val frameContent = dashboardPageLayout.newBuilder()
      .buildHtmlResponseBody {
        turbo_frame("backfill-$id-state") {
          div("flex items-start gap-2") {
            div("flex flex-col") {
              renderStateButtons(id, currentState, deletedAt)
            }
            div("flex items-center gap-2") {
              span("text-sm font-bold text-gray-500") { +"State:" }
              span("text-sm font-semibold text-gray-900") { +currentState.name }
            }
          }
        }
      }

    return Response(
      body = frameContent,
      statusCode = 200,
      headers = Headers.headersOf("Content-Type", MediaTypes.TEXT_HTML),
    )
  }

  fun TagConsumer<*>.renderStateButtons(id: String, currentState: BackfillState, deletedAt: Instant? = null) {
    getStateButton(currentState)?.let { button ->
      renderButton(id, "state", button, if (button.label == START_STATE_BUTTON_LABEL) "green" else "yellow")
    }
    getCancelButton(currentState)?.let { button ->
      renderButton(id, "state", button, "red")
    }
    getDeleteButton(currentState, deletedAt)?.let { button ->
      renderButton(id, "state", button, "gray")
    }
  }

  fun TagConsumer<*>.renderButton(id: String, fieldId: String, button: Link, color: String) {
    form(classes = "m-0 pb-1") {
      action = path(id)
      input {
        type = InputType.hidden
        name = "field_id"
        value = fieldId
      }
      input {
        type = InputType.hidden
        name = "field_value"
        value = button.href
      }
      button(
        classes = "rounded-full bg-$color-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-$color-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-$color-600",
      ) {
        type = ButtonType.submit
        +button.label
      }
    }
  }

  private fun handleRedirectResponse(id: String): Response<ResponseBody> {
    return Response(
      body = "go to /backfills/$id".toResponseBody(),
      statusCode = 303,
      headers = Headers.headersOf("Location", "/backfills/$id"),
    )
  }

  private fun getStateButton(state: BackfillState): Link? {
    return when (state) {
      BackfillState.PAUSED -> Link(
        label = START_STATE_BUTTON_LABEL,
        href = BackfillState.RUNNING.name,
      )
      // COMPLETE and CANCELLED represent final states.
      BackfillState.COMPLETE -> null
      BackfillState.CANCELLED -> null
      else -> Link(
        label = PAUSE_STATE_BUTTON_LABEL,
        href = BackfillState.PAUSED.name,
      )
    }
  }

  private fun getCancelButton(state: BackfillState): Link? {
    return when (state) {
      BackfillState.PAUSED -> Link(
        label = CANCEL_STATE_BUTTON_LABEL,
        href = BackfillState.CANCELLED.name,
      )
      else -> null
    }
  }

  private fun getDeleteButton(state: BackfillState, deletedAt: Instant?): Link? {
    if (deletedAt != null) {
      return null
    }
    return when (state) {
      BackfillState.COMPLETE, BackfillState.CANCELLED -> Link(
        label = DELETE_STATE_BUTTON_LABEL,
        href = "soft_delete",
      )
      else -> null
    }
  }

  companion object {
    private val logger = getLogger<BackfillShowButtonHandlerAction>()

    const val PATH = "/api/backfill/{id}/update"
    fun path(id: String) = PATH.replace("{id}", id)
    fun path(id: Long) = path(id.toString())
  }
}
