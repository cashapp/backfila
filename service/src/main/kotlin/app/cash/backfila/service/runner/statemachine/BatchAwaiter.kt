package app.cash.backfila.service.runner.statemachine

import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.RunBatchResponse
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbEventLog
import app.cash.backfila.service.persistence.DbRunPartition
import app.cash.backfila.service.runner.BackfillRunner
import java.time.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.hibernate.load
import okio.ByteString
import wisp.logging.getLogger

/**
 * Receives RunBatch RPC futures from the BatchRunner and handles the results, potentially
 * enqueueing a retry.
 */
class BatchAwaiter(
  private val backfillRunner: BackfillRunner,
  private val receiveChannel: ReceiveChannel<AwaitingRun>,
  private val rpcBackpressureChannel: ReceiveChannel<Unit>,
) {
  private val scannedRateCounter = RateCounter(backfillRunner.factory.clock)
  private val matchingRateCounter = RateCounter(backfillRunner.factory.clock)

  private var pkeyCursor: ByteString? = backfillRunner.metadata.pkeyCursor
  private var backfilledScannedRecordCount: Long = backfillRunner.metadata.backfilledScannedRecordCount
  private var backfilledMatchingRecordCount: Long = backfillRunner.metadata.backfilledMatchingRecordCount
  private var scannedRecordsPerMinute: Long? = null
  private var matchingRecordsPerMinute: Long? = null

  // TODO on shutdown can this wait for all rpcs to finish, with a ~5s time bound?
  fun run(
    scope: CoroutineScope,
  ) = scope.launch(CoroutineName("BatchAwaiter")) {
    logger.info { "BatchAwaiter started ${backfillRunner.logLabel()}" }
    main@ while (true) {
      val (initialBatch, initialRunBatchRpc, batchStartedAt) = try {
        receiveChannel.receive()
      } catch (e: CancellationException) {
        logger.info(e) { "BatchAwaiter job cancelled ${backfillRunner.logLabel()}" }
        break
      } catch (e: ClosedReceiveChannelException) {
        logger.info { "No more batches to await, completed ${backfillRunner.logLabel()}" }
        completePartition()
        break
      }

      var remainingBatch = initialBatch
      var runBatchRpc = initialRunBatchRpc
      var callStartedAt = batchStartedAt
      // Repeat this batch until it succeeds.
      retry@ while (true) {
        val nextRequest: Deferred<RunBatchResponse> = try {
          val response: RunBatchResponse = runBatchRpc.await()

          if (response.exception_stack_trace != null) {
            throw RunBatchException(response.exception_stack_trace)
          }

          if (!backfillRunner.runBatchBackoff.backingOff()) {
            if ((response.backoff_ms ?: 0) > 0) {
              backfillRunner.runBatchBackoff.addMillis(response.backoff_ms)
            } else if (backfillRunner.metadata.extraSleepMs > 0 && initialBatch.matching_record_count != 0L) {
              backfillRunner.runBatchBackoff.addMillis(backfillRunner.metadata.extraSleepMs)
            }
          }

          if (response.remaining_batch_range != null) {
            // We have a remaining_batch_range, continue the batch.
            remainingBatch = initialBatch.newBuilder()
              .batch_range(response.remaining_batch_range)
              .build()
            backoffAndSendRunBatchAsync(remainingBatch) {
              "${backfillRunner.logLabel()} continuing remaining range " +
                "${response.remaining_batch_range} of for partially completed batch $initialBatch"
            }
          } else {
            // We got a 200 response on the batch with no error or partial bookmark. This batch is
            // done.
            logger.info { "Runbatch finished for ${backfillRunner.logLabel()} $initialBatch" }

            backfillRunner.factory.metrics.runBatchSuccesses
              .labels(*backfillRunner.metricLabels).inc()
            backfillRunner.factory.metrics.runBatchCompletedRecordsMatching
              .labels(*backfillRunner.metricLabels)
              .inc(initialBatch.matching_record_count.toDouble())
            backfillRunner.factory.metrics.runBatchCompletedRecordsScanned
              .labels(*backfillRunner.metricLabels)
              .inc(initialBatch.scanned_record_count.toDouble())
            backfillRunner.onRpcSuccess()

            matchingRateCounter.add(initialBatch.matching_record_count)
            scannedRateCounter.add(initialBatch.scanned_record_count)

            pkeyCursor = initialBatch.batch_range.end
            backfilledScannedRecordCount += initialBatch.scanned_record_count
            backfilledMatchingRecordCount += initialBatch.matching_record_count
            scannedRecordsPerMinute = scannedRateCounter.projectedRate()
            matchingRecordsPerMinute = matchingRateCounter.projectedRate()

            if (backfillRunner.metadata.precomputingDone && matchingRecordsPerMinute!! > 0) {
              val remaining = (
                backfillRunner.metadata.computedMatchingRecordCount -
                  backfilledMatchingRecordCount
                )
              val etaMinutes = remaining.toDouble() / matchingRecordsPerMinute!!
              val etaMillis = etaMinutes * 60 * 1000
              backfillRunner.factory.metrics.eta
                .labels(*backfillRunner.metricLabels)
                .set(etaMillis)
            }

            break@retry
          }
        } catch (e: CancellationException) {
          logger.info(e) { "BatchAwaiter job cancelled ${backfillRunner.logLabel()}" }
          break@main
        } catch (e: Exception) {
          logger.info(e) { "Rpc failure when running batch for ${backfillRunner.logLabel()}" }

          backfillRunner.factory.metrics.runBatchFailures
            .labels(*backfillRunner.metricLabels).inc()
          backfillRunner.onRpcFailure(
            e,
            "running batch [${remainingBatch.batch_range.start.utf8()}, " +
              "${remainingBatch.batch_range.end.utf8()}]",
            Duration.between(callStartedAt, backfillRunner.factory.clock.instant()),
          )

          backoffAndSendRunBatchAsync(remainingBatch) {
            "${backfillRunner.logLabel()} running runbatch retry with range " +
              "${remainingBatch.batch_range} for $initialBatch"
          }
        }

        runBatchRpc = nextRequest
        // Reset the started clock for the next request
        callStartedAt = backfillRunner.factory.clock.instant()
      }

      // Signal to the rpc sender that there is more capacity to send rpcs.
      rpcBackpressureChannel.receive()
    }
  }

  private suspend fun CoroutineScope.backoffAndSendRunBatchAsync(
    batch: GetNextBatchRangeResponse.Batch,
    onRunMsg: () -> Any?,
  ): Deferred<RunBatchResponse> {
    // After backing off then run the request.
    if (backfillRunner.globalBackoff.backingOff()) {
      val backoffMs = backfillRunner.globalBackoff.backoffMs()
      logger.info {
        "BatchAwaiter ${backfillRunner.logLabel()} backing off for $backoffMs ms"
      }
      delay(backoffMs)
    }
    logger.info(onRunMsg)
    return backfillRunner.runBatchAsync(this, batch)
  }

  private fun completePartition() {
    val runComplete = backfillRunner.factory.transacter.transaction { session ->
      val dbRunPartition = session.load(backfillRunner.partitionId)
      dbRunPartition.run_state = BackfillState.COMPLETE
      updateProgress(dbRunPartition)

      session.save(
        DbEventLog(
          backfillRunner.backfillRunId,
          partition_id = dbRunPartition.id,
          type = DbEventLog.Type.STATE_CHANGE,
          message = "partition completed",
        ),
      )

      // If all states are COMPLETE the whole backfill will be completed.
      // If multiple partitions finish at the same time they will retry due to the hibernate
      // version mismatch on the DbBackfillRun.
      val partitions = dbRunPartition.backfill_run.partitions(
        session,
        backfillRunner.factory.queryFactory,
      )
      if (partitions.all { it.run_state == BackfillState.COMPLETE }) {
        dbRunPartition.backfill_run.complete()
        logger.info { "Backfill ${backfillRunner.backfillName} completed" }

        session.save(
          DbEventLog(
            backfillRunner.backfillRunId,
            type = DbEventLog.Type.STATE_CHANGE,
            message = "backfill completed",
          ),
        )

        return@transaction true
      }
      false
    }

    if (runComplete) {
      backfillRunner.factory.backfillRunListeners.forEach { it.runCompleted(backfillRunner.backfillRunId) }
    }
  }

  fun updateProgress(dbRunPartition: DbRunPartition) {
    dbRunPartition.pkey_cursor = pkeyCursor
    dbRunPartition.backfilled_scanned_record_count = backfilledScannedRecordCount
    dbRunPartition.backfilled_matching_record_count = backfilledMatchingRecordCount
    if (scannedRecordsPerMinute != null) {
      dbRunPartition.scanned_records_per_minute = scannedRecordsPerMinute
      dbRunPartition.matching_records_per_minute = matchingRecordsPerMinute
    }
  }

  companion object {
    private val logger = getLogger<BatchAwaiter>()
  }
}
