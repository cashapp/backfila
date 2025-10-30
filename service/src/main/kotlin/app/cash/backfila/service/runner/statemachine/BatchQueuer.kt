package app.cash.backfila.service.runner.statemachine

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse.Batch
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.service.runner.BackfillRunner
import com.google.common.base.Stopwatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.logging.getLogger

/**
 * Sends RPCs to the client service to compute batch ranges and sends them along a channel to the
 * BatchQueuer.
 */
class BatchQueuer(
  private val backfillRunner: BackfillRunner,
  numThreads: Int,
) {
  private val nextBatchChannel = VariableCapacityChannel<Batch>(
    capacity(numThreads),
    queueSizeChangeListener = { queueSize ->
      backfillRunner.factory.metrics.bufferedBatchesReadyToRun
        .labels(*backfillRunner.metricLabels)
        .set(queueSize.toDouble())
    },
  )

  fun nextBatchChannel(): ReceiveChannel<Batch> = nextBatchChannel.downstream()

  private fun capacity(numThreads: Int) =
    numThreads * backfillRunner.factory.backfilaConfig.batch_queue_thread_multiplier

  fun run(coroutineScope: CoroutineScope) = coroutineScope.launch(CoroutineName("BatchQueuer")) {
    nextBatchChannel.proxy(coroutineScope)

    logger.info {
      "BatchQueuer started ${backfillRunner.logLabel()} with buffer size=" +
        "${nextBatchChannel.capacity}"
    }

    // Start at the cursor we have in the DB, but after that we need to maintain our own,
    // since the DB stores how far we've completed batches, and we are likely ahead of that.
    var pkeyCursor = backfillRunner.metadata.pkeyCursor

    val stopwatch = Stopwatch.createUnstarted()

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
        // The client is expected to compute as many batches as possible up to computeCountLimit
        // and within computeTimeLimitMs. It may exceed the time limit when computing its last returned batch.
        val computeTimeLimitMs = 5_000L // half of HTTP timeout
        val computeCountLimit = nextBatchChannel.capacity
          .coerceAtLeast(backfillRunner.factory.backfilaConfig.minimum_batches_per_get_next_batch_call)

        stopwatch.reset()
        stopwatch.start()

        val response = backfillRunner.client.getNextBatchRange(
          GetNextBatchRangeRequest(
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
            false,
          ),
        )

        backfillRunner.factory.metrics.getNextBatchSuccesses
          .labels(*backfillRunner.metricLabels).inc()
        backfillRunner.factory.metrics.getNextBatchDuration.labels(*backfillRunner.metricLabels)
          .observe(stopwatch.elapsed().toMillis().toDouble())
        backfillRunner.factory.metrics.computedBatchCount
          .labels(*backfillRunner.metricLabels).inc(response.batches.size.toDouble())
        backfillRunner.factory.metrics.computedRecordsMatching
          .labels(*backfillRunner.metricLabels)
          .inc(response.batches.sumOf { it.matching_record_count.toDouble() })
        backfillRunner.factory.metrics.computedRecordsScanned
          .labels(*backfillRunner.metricLabels)
          .inc(response.batches.sumOf { it.scanned_record_count.toDouble() })
        backfillRunner.onRpcSuccess()

        if (response.batches.isEmpty()) {
          logger.info { "No more batches, finished computing for ${backfillRunner.logLabel()}" }
          nextBatchChannel.upstream().close()
          break
        }

        for (batch in response.batches) {
          nextBatchChannel.upstream().send(batch)
          pkeyCursor = batch.batch_range.end
        }
      } catch (e: CancellationException) {
        logger.info(e) { "BatchQueuer job cancelled ${backfillRunner.logLabel()}" }
        break
      } catch (e: Exception) {
        logger.info(e) { "Rpc failure when computing next batch for ${backfillRunner.logLabel()}" }
        backfillRunner.factory.metrics.getNextBatchFailures
          .labels(*backfillRunner.metricLabels).inc()
        backfillRunner.factory.metrics.getNextBatchDuration.labels(*backfillRunner.metricLabels)
          .observe(stopwatch.elapsed().toMillis().toDouble())
        backfillRunner.onRpcFailure(e, "computing batch", stopwatch.elapsed())
      }
    }
    logger.info { "BatchQueuer stopped ${backfillRunner.logLabel()}" }
  }

  companion object {
    private val logger = getLogger<BatchQueuer>()
  }
}
