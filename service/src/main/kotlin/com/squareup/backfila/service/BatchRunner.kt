package com.squareup.backfila.service

import com.squareup.protos.backfila.clientservice.GetNextBatchRangeResponse.Batch
import com.squareup.protos.backfila.clientservice.RunBatchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.logging.getLogger

data class AwaitingRun(
  val batch: Batch,
  val runBatchRpc: Deferred<RunBatchResponse>
)

class BatchRunner(
  private val backfillRunner: BackfillRunner,
  private val nextBatchChannel: ReceiveChannel<Batch>,
  private val numThreads: Int
) {
  // TODO resize on config change
  // 0 capacity is 1 at a time, etc, so subtract 1.
  private val runChannel = Channel<AwaitingRun>(capacity = numThreads - 1)

  /**
   * This channel just signals when more RPCs can be sent, since the above channel is opened up when
   * a Deferred RPC is read, not when the RPC completes, which would cause an extra RPC to be open.
   */
  private val rpcBackpressureChannel = Channel<Unit>(capacity = numThreads - 1)

  fun runChannel(): ReceiveChannel<AwaitingRun> = runChannel

  fun rpcBackpressureChannel(): ReceiveChannel<Unit> = rpcBackpressureChannel

  fun run(coroutineScope: CoroutineScope) = coroutineScope.launch {
    logger.info { "BatchRunner started ${backfillRunner.logLabel()} with numThreads=$numThreads" }

    while (true) {
      val batch = try {
        nextBatchChannel.receive()
      } catch (e: CancellationException) {
        logger.info(e) { "BatchRunner job cancelled ${backfillRunner.logLabel()}" }
        break
      } catch (e: ClosedReceiveChannelException) {
        logger.info { "Queuer closed, no more batches to run ${backfillRunner.logLabel()}" }
        runChannel.close()
        break
      }

      if (backfillRunner.backingOff()) {
        val backoffMs = backfillRunner.backoffMs()
        logger.info { "BatchRunner ${backfillRunner.logLabel()} backing off for $backoffMs" }
        delay(backoffMs)
      }

      logger.info { "Enqueuing run of batch $batch" }
      // TODO skip rpcs for batches with no matching records, but they still have to be processed
      //  to advance the cursor
      // Supervisor here allows us to handle the exception, rather than failing the job.
      val run = async(SupervisorJob()) {
        backfillRunner.client.runBatch(backfillRunner.runBatchRequest(batch))
      }
      runChannel.send(AwaitingRun(batch, run))
      rpcBackpressureChannel.send(Unit)
    }
    logger.info { "BatchRunner stopped ${backfillRunner.logLabel()}" }
  }

  companion object {
    private val logger = getLogger<BatchRunner>()
  }
}