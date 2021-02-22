package app.cash.backfila.service.runner.statemachine

import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse.Batch
import app.cash.backfila.protos.clientservice.RunBatchResponse
import app.cash.backfila.service.runner.BackfillRunner
import com.google.common.base.Stopwatch
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.logging.getLogger

/**
 * Receives batch ranges from the BatchQueuer and starts RPCs to the client service to run batches.
 * The futures are sent along a channel and are handled in the BatchAwaiter.
 */
class BatchRunner(
  private val backfillRunner: BackfillRunner,
  private val nextBatchChannel: ReceiveChannel<Batch>,
  private val numThreads: Int
) {
  private val runChannel = VariableCapacityChannel<AwaitingRun>(
    capacity(numThreads)
  )

  /**
   * This channel just signals when more RPCs can be sent, since the above channel is opened up when
   * a Deferred RPC is read, not when the RPC completes, which would cause an extra RPC to be open.
   */
  private val rpcBackpressureChannel = VariableCapacityChannel<Unit>(
    capacity(numThreads)
  )

  fun runChannel(): ReceiveChannel<AwaitingRun> = runChannel.downstream()

  fun rpcBackpressureChannel(): ReceiveChannel<Unit> = rpcBackpressureChannel.downstream()

  private fun capacity(numThreads: Int) = numThreads

  fun run(coroutineScope: CoroutineScope) = coroutineScope.launch {
    logger.info { "BatchRunner started ${backfillRunner.logLabel()} with numThreads=$numThreads" }

    runChannel.proxy(coroutineScope)
    rpcBackpressureChannel.proxy(coroutineScope)
    // Prefill the backpressure channel with one entry since it will be consumed and buffered
    // immediately.
    rpcBackpressureChannel.upstream().send(Unit)

    while (true) {
      // Use the latest metadata snapshot.
      val metadata = backfillRunner.metadata
      // Update channel size if the user changed numThreads.
      val newCapacity = capacity(metadata.numThreads)
      if (newCapacity != runChannel.capacity) {
        logger.info { "Updated channel capacity to $newCapacity" }
        runChannel.capacity = newCapacity
        rpcBackpressureChannel.capacity = newCapacity
      }

      val stopwatch = Stopwatch.createStarted()

      val batch = try {
        nextBatchChannel.receive()
      } catch (e: CancellationException) {
        logger.info(e) { "BatchRunner job cancelled ${backfillRunner.logLabel()}" }
        break
      } catch (e: ClosedReceiveChannelException) {
        logger.info { "Queuer closed, no more batches to run ${backfillRunner.logLabel()}" }
        runChannel.upstream().close()
        break
      }
      if (stopwatch.elapsed() > Duration.ofMillis(500)) {
        logger.info {
          "Runner stalled ${stopwatch.elapsed()} ms waiting for batch from " +
            "queuer ${backfillRunner.logLabel()}"
        }
      }

      if (backfillRunner.globalBackoff.backingOff()) {
        val backoffMs = backfillRunner.globalBackoff.backoffMs()
        logger.info { "BatchRunner ${backfillRunner.logLabel()} backing off for $backoffMs" }
        delay(backoffMs)
      }
      // Backoff specifically applied only to the starting of the next runBatch request. In addition
      // to any overall backfill backoff.
      if (backfillRunner.runBatchBackoff.backingOff()) {
        val backoffMs = backfillRunner.runBatchBackoff.backoffMs()
        delay(backoffMs)
      }

      logger.info { "Enqueuing run of batch $batch" }
      val run = if (batch.matching_record_count == 0L) {
        // Skip RPCs for batches with no matching records, but still report it to the awaiter to
        // update cursors.
        async { RunBatchResponse.Builder().build() }
      } else {
        backfillRunner.runBatchAsync(this, batch)
      }
      runChannel.upstream().send(
        AwaitingRun(
          batch,
          run,
          backfillRunner.factory.clock.instant()
        )
      )
      rpcBackpressureChannel.upstream().send(Unit)
    }
    logger.info { "BatchRunner stopped ${backfillRunner.logLabel()}" }
  }

  companion object {
    private val logger = getLogger<BatchRunner>()
  }
}
