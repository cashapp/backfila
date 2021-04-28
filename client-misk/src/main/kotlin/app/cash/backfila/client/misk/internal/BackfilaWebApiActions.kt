package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.misk.client.BackfilaClientLoggingSetupProvider
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import misk.security.authz.Authenticated
import misk.web.AvailableWhenDegraded
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.interceptors.LogRequestResponse
import misk.web.mediatype.MediaTypes
import org.apache.commons.lang3.exception.ExceptionUtils
import wisp.logging.getLogger
import javax.inject.Inject

internal class PrepareBackfillAction @Inject constructor(
  private val operatorFactory: BackfillOperatorFactory,
  private val loggingSetupProvider: BackfilaClientLoggingSetupProvider
) : WebAction {
  @Post("/backfila/prepare-backfill")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  @LogRequestResponse(bodySampling = 1.0, errorBodySampling = 1.0)
  @AvailableWhenDegraded
  fun prepareBackfill(@RequestBody request: PrepareBackfillRequest): PrepareBackfillResponse {
    return loggingSetupProvider.withBackfillRunLogging(request.backfill_name, request.backfill_id) {
      logger.info { "Preparing backfill `${request.backfill_name}::${request.backfill_id}`" }

      val operator = operatorFactory.create(request.backfill_name, request.backfill_id)
      return@withBackfillRunLogging operator.prepareBackfill(request)
    }
  }

  companion object {
    val logger = getLogger<PrepareBackfillAction>()
  }
}

internal class GetNextBatchRangeAction @Inject constructor(
  private val operatorFactory: BackfillOperatorFactory,
  private val loggingSetupProvider: BackfilaClientLoggingSetupProvider
) : WebAction {
  @Post("/backfila/get-next-batch-range")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  @AvailableWhenDegraded
  fun getNextBatchRange(@RequestBody request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    return loggingSetupProvider.withBackfillPartitionLogging(
      request.backfill_name,
      request.backfill_id,
      request.partition_name
    ) {
      logger.info {
        "Computing batch for backfill `${request.backfill_name}::${request.partition_name}" +
          "::${request.backfill_id}`. Previous end: `${request.previous_end_key}`"
      }

      val operator = operatorFactory.create(request.backfill_name, request.backfill_id)

      val nextBatchRange = operator.getNextBatchRange(request)
      logger.info {
        "Next batches computed for backfill " +
          "`${request.backfill_name}::${request.partition_name}::${request.backfill_id}`. " +
          "${nextBatchRange.batches}"
      }
      return@withBackfillPartitionLogging nextBatchRange
    }
  }

  companion object {
    val logger = getLogger<GetNextBatchRangeAction>()
  }
}

internal class RunBatchAction @Inject constructor(
  private val operatorFactory: BackfillOperatorFactory,
  private val loggingSetupProvider: BackfilaClientLoggingSetupProvider
) : WebAction {
  @Post("/backfila/run-batch")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  @AvailableWhenDegraded
  fun runBatch(@RequestBody request: RunBatchRequest): RunBatchResponse {
    return loggingSetupProvider.withBackfillPartitionLogging(
      request.backfill_name,
      request.backfill_id,
      request.partition_name
    ) {
      logger.info {
        "Running backfila batch " +
          "`${request.backfill_name}::${request.partition_name}::${request.backfill_id}`: " +
          "[${request.batch_range.start.utf8()}, ${request.batch_range.end.utf8()}]"
      }

      val operator = operatorFactory.create(request.backfill_name, request.backfill_id)
      try {
        return@withBackfillPartitionLogging operator.runBatch(request)
      } catch (exception: Exception) {
        return@withBackfillPartitionLogging RunBatchResponse.Builder()
          .exception_stack_trace(ExceptionUtils.getStackTrace(exception))
          .build()
      }
    }
  }

  companion object {
    val logger = getLogger<RunBatchAction>()
  }
}
