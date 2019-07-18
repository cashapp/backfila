package com.squareup.backfila.service

import com.squareup.protos.backfila.clientservice.RunBatchResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.hibernate.load
import misk.logging.getLogger

class BatchAwaiter(
  private val backfillRunner: BackfillRunner,
  private val receiveChannel: ReceiveChannel<AwaitingRun>,
  private val rpcBackpressureChannel: ReceiveChannel<Unit>
) {
  // TODO on shutdown can this wait for all rpcs to finish, with a ~5s time bound?
  fun run(
    scope: CoroutineScope
  ) = scope.launch {
    logger.info { "BatchAwaiter started ${backfillRunner.logLabel()}" }
    main@ while (true) {
      var (batch, runBatchRpc) = try {
        receiveChannel.receive()
      } catch (e: CancellationException) {
        logger.info(e) { "BatchAwaiter job cancelled ${backfillRunner.logLabel()}" }
        break
      } catch (e: ClosedReceiveChannelException) {
        logger.info { "No more batches to await, completed ${backfillRunner.logLabel()}" }
        completeInstance()
        break
      }

      // Repeat this batch until it succeeds.
      retry@ while (true) {
        try {
          // TODO read response for backoff
          val response: RunBatchResponse = runBatchRpc.await()

          logger.info { "Runbatch finished for ${backfillRunner.logLabel()} $batch" }

          backfillRunner.onRpcSuccess()

          // Track our progress in DB for when another runner takes over.
          // TODO update this less often, probably in the lease updater task
          backfillRunner.factory.transacter.transaction { session ->
            val dbRunInstance = session.load(backfillRunner.instanceId)
            dbRunInstance.pkey_cursor = batch.batch_range.end
            dbRunInstance.backfilled_scanned_record_count += batch.scanned_record_count
            dbRunInstance.backfilled_matching_record_count += batch.matching_record_count
          }
          break@retry
        } catch (e: CancellationException) {
          logger.info(e) { "BatchAwaiter job cancelled ${backfillRunner.logLabel()}" }
          break@main
        } catch (e: Exception) {
          logger.info(e) { "Rpc failure when running batch for ${backfillRunner.logLabel()}" }
          backfillRunner.onRpcFailure()

          if (backfillRunner.backingOff()) {
            val backoffMs = backfillRunner.backoffMs()
            logger.info {
              "BatchAwaiter ${backfillRunner.logLabel()} backing off for $backoffMs ms"
            }
            delay(backoffMs)
          }
          // Supervisor here allows us to handle the exception, rather than failing the job.
          runBatchRpc = async(SupervisorJob()) {
            backfillRunner.client.runBatch(backfillRunner.runBatchRequest(batch))
          }
          logger.info { "${backfillRunner.logLabel()} enqueued runbatch retry for $batch" }
        }
      }

      // Signal to the rpc sender that there is more capacity to send rpcs.
      rpcBackpressureChannel.receive()
    }
  }

  fun completeInstance() {
    backfillRunner.factory.transacter.transaction { session ->
      val dbRunInstance = session.load(backfillRunner.instanceId)
      dbRunInstance.run_state = BackfillState.COMPLETE

      // If all states are COMPLETE the whole backfill will be completed.
      // If multiple instances finish at the same time they will retry due to the hibernate
      // version mismatch.
      val instances = dbRunInstance.backfill_run.instances(session,
          backfillRunner.factory.queryFactory)
      if (instances.all { it.run_state == BackfillState.COMPLETE }) {
        dbRunInstance.backfill_run.complete()
        logger.info { "Backfill ${backfillRunner.backfillName} completed" }
        // TODO post-completion tasks...
      }
    }
  }

  companion object {
    private val logger = getLogger<BatchAwaiter>()
  }
}
