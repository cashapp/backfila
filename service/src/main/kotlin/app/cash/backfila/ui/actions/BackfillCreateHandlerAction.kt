package app.cash.backfila.ui.actions

import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.UiPartition
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.ui.components.AlertError
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.pages.BackfillCreateAction.BackfillCreateField
import app.cash.backfila.ui.pages.BackfillCreateAction.RangeOption
import app.cash.backfila.ui.pages.BackfillShowAction
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.div
import misk.logging.getLogger
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.HttpCall
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers
import okio.ByteString.Companion.encodeUtf8

@Singleton
class BackfillCreateHandlerAction @Inject constructor(
  private val createBackfillAction: CreateBackfillAction,
  private val getBackfillStatusAction: GetBackfillStatusAction,
  private val dashboardPageLayout: DashboardPageLayout,
  private val httpCall: ActionScoped<HttpCall>,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(): Response<ResponseBody> {
    val response = try {
      // Parse form
      val formFieldNames = this.httpCall.get().asOkHttpRequest().url.queryParameterNames
      val formFields = formFieldNames.associateWith { this.httpCall.get().asOkHttpRequest().url.queryParameter(it) }

      // Submit create call
      val createRequestBuilder = CreateBackfillRequest.Builder()
      formFields[BackfillCreateField.BACKFILL_NAME.fieldId]?.ifNotBlank { createRequestBuilder.backfill_name(it) }
      createRequestBuilder.dry_run(
        when (formFields[BackfillCreateField.DRY_RUN.fieldId]) {
          // Unchecked box in UI will not send a value
          "off", null -> false
          else -> true
        },
      )

      // Handle range options for cloned backfills
      when (formFields[BackfillCreateField.RANGE_OPTION.fieldId]) {
        RangeOption.CONTINUE.value -> {
          // Get the last processed position from the original backfill
          val backfillId = formFields[BackfillCreateField.BACKFILL_ID_TO_CLONE.fieldId]?.toLongOrNull()
          backfillId?.let { id ->
            val range = continueRange(getBackfillStatusAction.status(id).partitions)
            range.start?.let { createRequestBuilder.pkey_range_start(it.encodeUtf8()) }
            range.end?.let { createRequestBuilder.pkey_range_end(it.encodeUtf8()) }
          }
        }
        RangeOption.RESTART.value -> {
          // Use the original range but start from beginning
          val backfillId = formFields[BackfillCreateField.BACKFILL_ID_TO_CLONE.fieldId]?.toLongOrNull()
          backfillId?.let { id ->
            val range = restartRange(getBackfillStatusAction.status(id).partitions)
            range.start?.let { createRequestBuilder.pkey_range_start(it.encodeUtf8()) }
            range.end?.let { createRequestBuilder.pkey_range_end(it.encodeUtf8()) }
          }
        }
        else -> {
          // For new range or non-clone cases, use the form values
          formFields[BackfillCreateField.RANGE_START.fieldId]?.ifNotBlank { createRequestBuilder.pkey_range_start(it.encodeUtf8()) }
          formFields[BackfillCreateField.RANGE_END.fieldId]?.ifNotBlank { createRequestBuilder.pkey_range_end(it.encodeUtf8()) }
        }
      }
      formFields[BackfillCreateField.BATCH_SIZE.fieldId]?.ifNotBlank { createRequestBuilder.batch_size(it.toLongOrNull()) }
      formFields[BackfillCreateField.SCAN_SIZE.fieldId]?.ifNotBlank { createRequestBuilder.scan_size(it.toLongOrNull()) }
      formFields[BackfillCreateField.THREADS_PER_PARTITION.fieldId]?.ifNotBlank { createRequestBuilder.num_threads(it.toIntOrNull()) }
      formFields[BackfillCreateField.EXTRA_SLEEP_MS.fieldId]?.ifNotBlank { createRequestBuilder.extra_sleep_ms(it.toLongOrNull()) }
      formFields[BackfillCreateField.BACKOFF_SCHEDULE.fieldId]?.ifNotBlank { createRequestBuilder.backoff_schedule(it) }
      val customParameters = formFields.filter { it.key.startsWith(BackfillCreateField.CUSTOM_PARAMETER_PREFIX.fieldId) && !it.value.isNullOrBlank() }
        .map { it.key.removePrefix(BackfillCreateField.CUSTOM_PARAMETER_PREFIX.fieldId) to it.value?.encodeUtf8() }.toMap()
      if (customParameters.isNotEmpty()) {
        createRequestBuilder.parameter_map(customParameters)
      }

      createBackfillAction.create(
        service = formFields[BackfillCreateField.SERVICE.fieldId]!!,
        variant = formFields[BackfillCreateField.VARIANT.fieldId]!!,
        request = createRequestBuilder.build(),
      )
    } catch (e: Exception) {
      // Since this action is only hit from the UI, catch any validation errors and show them to the user
      val errorHtmlResponseBody = dashboardPageLayout.newBuilder()
        .buildHtmlResponseBody {
          div("py-20") {
            AlertError(message = "Backfill create or clone failed: $e", label = "Try Again", onClick = "history.back(); return false;")
          }
        }
      logger.error(e) { "Backfill create or clone failed $e" }
      return Response(
        body = errorHtmlResponseBody,
        statusCode = 200,
        headers = Headers.headersOf("Content-Type", MediaTypes.TEXT_HTML),
      )
    }

    val id = response.backfill_run_id

    return Response(
      body = "go to ${BackfillShowAction.path(id)}".toResponseBody(),
      statusCode = 303,
      headers = Headers.headersOf("Location", BackfillShowAction.path(id)),
    )
  }

  /** The range a clone should start from, in the same string form the create form submits. */
  internal data class CloneRange(val start: String?, val end: String?)

  companion object {
    /**
     * A clone carries one range for every partition of the new backfill, so a position that differs
     * between the source partitions cannot be carried over at all.
     *
     * Each partition holds its own cursor, so continuing is only expressible when the source has a
     * single partition. Taking any one partition's cursor for all of them would rewind the
     * partitions that ran ahead of it, and skip every row between the cursor and the true position
     * of the partitions that lag behind it.
     */
    internal fun continueRange(partitions: List<UiPartition>): CloneRange {
      require(partitions.size == 1) {
        "Cannot continue a backfill that has ${partitions.size} partitions. Each partition has its" +
          " own cursor, and a clone applies one range to every partition, so continuing would" +
          " rewind the partitions that ran ahead and skip rows in the partitions that lag behind." +
          " Clone it with a new range instead."
      }
      val partition = partitions.single()
      return CloneRange(partition.pkey_cursor ?: partition.pkey_start, partition.pkey_end)
    }

    /**
     * Restarting can carry the range over when every source partition shares it, which is the case
     * when the original run was given an explicit range. When the partitions computed their own
     * ranges those differ, and no single range reproduces them, so the range is left unset and each
     * partition of the clone computes its own again.
     */
    internal fun restartRange(partitions: List<UiPartition>): CloneRange {
      val starts = partitions.mapTo(mutableSetOf()) { it.pkey_start }
      val ends = partitions.mapTo(mutableSetOf()) { it.pkey_end }
      return if (starts.size == 1 && ends.size == 1) {
        CloneRange(starts.single(), ends.single())
      } else {
        CloneRange(null, null)
      }
    }

    private val logger = getLogger<BackfillCreateHandlerAction>()

    const val PATH = "/api/backfill/create"

    private fun <T>String?.ifNotBlank(block: (String) -> T) = if (this.isNullOrBlank()) null else block(this)
  }
}
