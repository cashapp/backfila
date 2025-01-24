package app.cash.backfila.ui.actions

import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.ui.pages.BackfillCreateAction.BackfillCreateField
import javax.inject.Inject
import javax.inject.Singleton
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
  private val httpCall: ActionScoped<HttpCall>,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(): Response<ResponseBody> {
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
    formFields[BackfillCreateField.RANGE_START.fieldId]?.ifNotBlank { createRequestBuilder.pkey_range_start(it.encodeUtf8()) }
    formFields[BackfillCreateField.RANGE_END.fieldId]?.ifNotBlank { createRequestBuilder.pkey_range_end(it.encodeUtf8()) }
    formFields[BackfillCreateField.BATCH_SIZE.fieldId]?.ifNotBlank { createRequestBuilder.batch_size(it.toLongOrNull()) }
    formFields[BackfillCreateField.SCAN_SIZE.fieldId]?.ifNotBlank { createRequestBuilder.scan_size(it.toLongOrNull()) }
    formFields[BackfillCreateField.THREADS_PER_PARTITION.fieldId]?.ifNotBlank { createRequestBuilder.num_threads(it.toIntOrNull()) }
    formFields[BackfillCreateField.EXTRA_SLEEP_MS.fieldId]?.ifNotBlank { createRequestBuilder.extra_sleep_ms(it.toLongOrNull()) }
    formFields[BackfillCreateField.BACKOFF_SCHEDULE.fieldId]?.ifNotBlank { createRequestBuilder.backoff_schedule(it) }
    val customParameters = formFields.filter { it.key.startsWith(BackfillCreateField.CUSTOM_PARAMETER_PREFIX.fieldId) }
      .map { it.key.removePrefix(BackfillCreateField.CUSTOM_PARAMETER_PREFIX.fieldId) to it.value?.encodeUtf8() }.toMap()
    if (customParameters.isNotEmpty()) {
      createRequestBuilder.parameter_map(customParameters)
    }

    val response = createBackfillAction.create(
      service = formFields[BackfillCreateField.SERVICE.fieldId]!!,
      variant = formFields[BackfillCreateField.VARIANT.fieldId]!!,
      request = createRequestBuilder.build(),
    )

    val id = response.backfill_run_id

    return Response(
      body = "go to /backfills/$id".toResponseBody(),
      statusCode = 303,
      headers = Headers.headersOf("Location", "/backfills/$id"),
    )
  }

  companion object {
    const val PATH = "/api/backfill/create"

    private fun <T>String?.ifNotBlank(block: (String) -> T) = if (this.isNullOrBlank()) null else block(this)
  }
}
