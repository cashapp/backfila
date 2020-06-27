package app.cash.backfila.service.runner.statemachine

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse.Batch
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.service.runner.BackfillRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.logging.getLogger

class BatchQueuer(
  private val backfillRunner: BackfillRunner,
  numThreads: Int
) {
  private val nextBatchChannel = VariableCapacityChannel<Batch>(
      capacity(numThreads)
  )

  fun nextBatchChannel(): ReceiveChannel<Batch> = nextBatchChannel.downstream()

  private fun capacity(numThreads: Int) = numThreads * 2

  fun run(coroutineScope: CoroutineScope) = coroutineScope.launch {
    nextBatchChannel.proxy(coroutineScope)

    logger.info { "BatchQueuer started ${backfillRunner.logLabel()} with buffer size=" +
        "${nextBatchChannel.capacity}" }

    // Start at the cursor we have in the DB, but after that we need to maintain our own,
    // since the DB stores how far we've completed batches, and we are likely ahead of that.
    var pkeyCursor = backfillRunner.metadata.pkeyCursor

    while (true) {
      // Use the latest metadata snapshot.
      val metadata = backfillRunner.metadata

      // Update channel size if the user changed numThreads.
      val newCapacity = capacity(metadata.numThreads)
      if (newCapacity != nextBatchChannel.capacity) {
        nextBatchChannel.capacity = newCapacity
      }

      if (backfillRunner.globalBackoff.backingOff()) {
        val backoffMs = backfillRunner.globalBackoff.backoffMs()
        logger.info { "BatchQueuer ${backfillRunner.logLabel()} backing off for $backoffMs" }
        delay(backoffMs)
      }

      try {
        val computeTimeLimitMs = 5_000L // half of HTTP timeout
        val computeCountLimit = nextBatchChannel.capacity
        val response = backfillRunner.client.getNextBatchRange(GetNextBatchRangeRequest(
            metadata.backfillRunId.toString(),
            backfillRunner.backfillName,
            backfillRunner.partitionName,
            metadata.batchSize,
            metadata.scanSize,
            pkeyCursor,
            KeyRange(metadata.pkeyStart, metadata.pkeyEnd),
            metadata.parameters,
            computeTimeLimitMs,
            computeCountLimit.toLong(),
            metadata.dryRun,
            false
        ))

        backfillRunner.onRpcSuccess()

        if (response.batches.isEmpty()) {
          logger.info { "No more batches, finished computing for ${backfillRunner.logLabel()}" }
          nextBatchChannel.upstream().close()
          break
        } else {
          for (batch in response.batches) {
            nextBatchChannel.upstream().send(batch)
            pkeyCursor = batch.batch_range.end
          }
        }
      } catch (e: CancellationException) {
        logger.info(e) { "BatchQueuer job cancelled ${backfillRunner.logLabel()}" }
        break
      } catch (e: Exception) {
        logger.info(e) { "Rpc failure when computing next batch for ${backfillRunner.logLabel()}" }
        backfillRunner.onRpcFailure()
      }
    }
    logger.info { "BatchQueuer stopped ${backfillRunner.logLabel()}" }
  }

  companion object {
    private val logger = getLogger<BatchQueuer>()
  }
}
