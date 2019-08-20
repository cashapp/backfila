package com.squareup.backfila.service

import com.squareup.protos.backfila.clientservice.GetNextBatchRangeRequest
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeResponse.Batch
import com.squareup.protos.backfila.clientservice.KeyRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.logging.getLogger

class BatchQueuer(
  private val backfillRunner: BackfillRunner,
  numThreads: Int
) {
  // 0 capacity is 1 at a time, etc, so subtract 1.
  private val capacity = numThreads * 2 - 1
  // TODO resize on config change
  private val nextBatchChannel = Channel<Batch>(capacity)

  fun nextBatchChannel(): ReceiveChannel<Batch> = nextBatchChannel

  fun run(coroutineScope: CoroutineScope) = coroutineScope.launch {
    logger.info { "BatchQueuer started ${backfillRunner.logLabel()} with buffer size=$capacity" }

    // Start at the cursor we have in the DB, but after that we need to maintain our own,
    // since the DB stores how far we've completed batches, and we are likely ahead of that.
    var pkeyCursor = backfillRunner.metadata.pkeyCursor

    while (true) {
      // Use the latest metadata snapshot.
      val metadata = backfillRunner.metadata

      if (backfillRunner.globalBackoff.backingOff()) {
        val backoffMs = backfillRunner.globalBackoff.backoffMs()
        logger.info { "BatchQueuer ${backfillRunner.logLabel()} backing off for $backoffMs" }
        delay(backoffMs)
      }

      try {
        val computeTimeLimitMs = 5_000L // half of HTTP timeout
        val computeCountLimit = capacity
        val response = backfillRunner.client.getNextBatchRange(GetNextBatchRangeRequest(
            metadata.backfillRunId.toString(),
            backfillRunner.backfillName,
            backfillRunner.instanceName,
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
          nextBatchChannel.close()
          break
        } else {
          for (batch in response.batches) {
            nextBatchChannel.send(batch)
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