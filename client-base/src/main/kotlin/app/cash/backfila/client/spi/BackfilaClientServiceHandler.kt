package app.cash.backfila.client.spi

import app.cash.backfila.client.BackfilaClientLoggingSetupProvider
import app.cash.backfila.client.UnknownBackfillException
import app.cash.backfila.client.internal.BackfillOperatorFactory
import app.cash.backfila.protos.clientservice.FinalizeBackfillRequest
import app.cash.backfila.protos.clientservice.FinalizeBackfillResponse
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.base.Stopwatch
import javax.inject.Inject
import org.apache.commons.lang3.exception.ExceptionUtils
import wisp.logging.getLogger

/**
 * The various Backfila ClientService implementations invoke this from their service handlers to get
 * default backfill handling logic.
 *
 * This is used by ClientService implementations and nothing else.
 */
class BackfilaClientServiceHandler @Inject constructor(
  private val operatorFactory: BackfillOperatorFactory,
  private val loggingSetupProvider: BackfilaClientLoggingSetupProvider,
) {
  @Throws(UnknownBackfillException::class)
  fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    return loggingSetupProvider.withBackfillRunLogging(request.backfill_name, backfillId = null) {
      logger.info { "Preparing backfill `${request.backfill_name}`" }
      val stopwatch = Stopwatch.createStarted()

      val operator = operatorFactory.create(request.backfill_name)
      val prepareResult = operator.prepareBackfill(request)

      logger.info { "Prepared backfill `${request.backfill_name}` in $stopwatch"  }
      return@withBackfillRunLogging prepareResult
    }
  }

  @Throws(UnknownBackfillException::class)
  fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    return loggingSetupProvider.withBackfillPartitionLogging(
      request.backfill_name,
      request.backfill_id,
      request.partition_name,
    ) {
      logger.info {
        "Computing batch for backfill `${request.backfill_name}::${request.partition_name}" +
          "::${request.backfill_id}`. Previous end: `${request.previous_end_key}`"
      }
      val stopwatch = Stopwatch.createStarted()

      val operator = operatorFactory.create(request.backfill_name, request.backfill_id)

      val nextBatchRange = operator.getNextBatchRange(request)
      logger.info {
        "Next batches computed for backfill " +
          "`${request.backfill_name}::${request.partition_name}::${request.backfill_id}`. " +
          "${nextBatchRange.batches} in $stopwatch"
      }
      return@withBackfillPartitionLogging nextBatchRange
    }
  }

  @Throws(UnknownBackfillException::class)
  fun runBatch(request: RunBatchRequest): RunBatchResponse {
    return loggingSetupProvider.withBackfillPartitionLogging(
      request.backfill_name,
      request.backfill_id,
      request.partition_name,
    ) {
      logger.info {
        "Running backfila batch " +
          "`${request.backfill_name}::${request.partition_name}::${request.backfill_id}`: " +
          "[${request.batch_range.start.utf8()}, ${request.batch_range.end.utf8()}]"
      }
      val stopwatch = Stopwatch.createStarted()

      val operator = operatorFactory.create(request.backfill_name, request.backfill_id)
      try {
        val runBatchResult = operator.runBatch(request)
        logger.info {
          "Ran backfila batch " +
              "`${request.backfill_name}::${request.partition_name}::${request.backfill_id}`: " +
              "[${request.batch_range.start.utf8()}, ${request.batch_range.end.utf8()}] in $stopwatch"
        }
        return@withBackfillPartitionLogging runBatchResult
      } catch (exception: Exception) {
        return@withBackfillPartitionLogging RunBatchResponse.Builder()
          .exception_stack_trace(ExceptionUtils.getStackTrace(exception))
          .build()
      }
    }
  }

  @Throws(UnknownBackfillException::class)
  fun finalizeBackfill(request: FinalizeBackfillRequest): FinalizeBackfillResponse {
    return loggingSetupProvider.withBackfillRunLogging(request.backfill_name, request.backfill_id) {
      logger.info { "Finalizing backfill `${request.backfill_name}::${request.backfill_id}`" }

      // This is a stub for now
      return@withBackfillRunLogging FinalizeBackfillResponse()
    }
  }

  companion object {
    val logger = getLogger<BackfilaClientServiceHandler>()
  }
}
