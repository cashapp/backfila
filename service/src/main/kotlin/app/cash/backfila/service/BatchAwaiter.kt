package app.cash.backfila.service

import app.cash.backfila.protos.clientservice.RunBatchResponse
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

class RunBatchException(stackTrace: String) : Exception()

class BatchAwaiter(
  private val backfillRunner: BackfillRunner,
  private val receiveChannel: ReceiveChannel<AwaitingRun>,
  private val rpcBackpressureChannel: ReceiveChannel<Unit>
) {
  private val scannedRateCounter = RateCounter(backfillRunner.factory.clock)
  private val matchingRateCounter = RateCounter(backfillRunner.factory.clock)

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
        completePartition()
        break
      }

      // Repeat this batch until it succeeds.
      retry@ while (true) {
        try {
          val response: RunBatchResponse = runBatchRpc.await()

          if (response.exception_stack_trace != null) {
            throw RunBatchException(response.exception_stack_trace)
          }

          if (!backfillRunner.runBatchBackoff.backingOff()) {
            if (response.backoff_ms ?: 0 > 0) {
              backfillRunner.runBatchBackoff.addMillis(response.backoff_ms)
            } else if (backfillRunner.metadata.extraSleepMs > 0) {
              backfillRunner.runBatchBackoff.addMillis(backfillRunner.metadata.extraSleepMs)
            }
          }

          logger.info { "Runbatch finished for ${backfillRunner.logLabel()} $batch" }

          backfillRunner.onRpcSuccess()

          matchingRateCounter.add(batch.matching_record_count)
          scannedRateCounter.add(batch.scanned_record_count)
          // Track our progress in DB for when another runner takes over.
          // TODO update this less often, probably in the lease updater task
          backfillRunner.factory.transacter.transaction { session ->
            val dbRunPartition = session.load(backfillRunner.partitionId)
            dbRunPartition.pkey_cursor = batch.batch_range.end
            dbRunPartition.backfilled_scanned_record_count += batch.scanned_record_count
            dbRunPartition.backfilled_matching_record_count += batch.matching_record_count
            dbRunPartition.scanned_records_per_minute = scannedRateCounter.projectedRate()
            dbRunPartition.matching_records_per_minute = matchingRateCounter.projectedRate()
          }
          break@retry
        } catch (e: CancellationException) {
          logger.info(e) { "BatchAwaiter job cancelled ${backfillRunner.logLabel()}" }
          break@main
        } catch (e: Exception) {
          logger.info(e) { "Rpc failure when running batch for ${backfillRunner.logLabel()}" }
          backfillRunner.onRpcFailure()

          if (e is RunBatchException) {
            // TODO: write exception to event log
          }

          if (backfillRunner.globalBackoff.backingOff()) {
            val backoffMs = backfillRunner.globalBackoff.backoffMs()
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

  fun completePartition() {
    val runComplete = backfillRunner.factory.transacter.transaction { session ->
      val dbRunPartition = session.load(backfillRunner.partitionId)
      dbRunPartition.run_state = BackfillState.COMPLETE

      // If all states are COMPLETE the whole backfill will be completed.
      // If multiple partitions finish at the same time they will retry due to the hibernate
      // version mismatch on the DbBackfillRun.
      val partitions = dbRunPartition.backfill_run.partitions(session,
          backfillRunner.factory.queryFactory)
      if (partitions.all { it.run_state == BackfillState.COMPLETE }) {
        dbRunPartition.backfill_run.complete()
        logger.info { "Backfill ${backfillRunner.backfillName} completed" }
        // TODO audit log
        return@transaction true
      }
      false
    }

    if (runComplete) {
      backfillRunner.factory.slackHelper.runCompleted(backfillRunner.backfillRunId)
    }
  }

  companion object {
    private val logger = getLogger<BatchAwaiter>()
  }
}
