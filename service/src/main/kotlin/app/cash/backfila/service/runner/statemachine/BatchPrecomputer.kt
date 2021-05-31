package app.cash.backfila.service.runner.statemachine

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.service.persistence.DbEventLog
import app.cash.backfila.service.persistence.DbRunPartition
import app.cash.backfila.service.runner.BackfillRunner
import com.google.common.base.Stopwatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.hibernate.load
import okio.ByteString
import wisp.logging.getLogger

class BatchPrecomputer(
  private val backfillRunner: BackfillRunner
) {
  private var pkeyCursor: ByteString? = backfillRunner.metadata.precomputingPkeyCursor
  private var computedScannedRecordCount: Long = backfillRunner.metadata.computedScannedRecordCount
  private var computedMatchingRecordCount: Long = backfillRunner.metadata.computedMatchingRecordCount

  fun run(coroutineScope: CoroutineScope) = coroutineScope.launch(CoroutineName("Precomputer")) {
    logger.info { "BatchPrecomputer started ${backfillRunner.logLabel()}" }

    val stopwatch = Stopwatch.createUnstarted()

    while (true) {
      // Use the latest metadata snapshot.
      val metadata = backfillRunner.metadata

      if (metadata.precomputingDone) {
        break
      }

      if (backfillRunner.globalBackoff.backingOff()) {
        val backoffMs = backfillRunner.globalBackoff.backoffMs()
        logger.info { "BatchPrecomputer ${backfillRunner.logLabel()} backing off for $backoffMs" }
        delay(backoffMs)
      }

      try {
        val computeTimeLimitMs = 5_000L // half of HTTP timeout
        // Just give us a ton of batches!
        val computeCountLimit = 100L

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
            computeCountLimit,
            metadata.dryRun,
            true
          )
        )

        backfillRunner.onRpcSuccess()

        if (response.batches.isEmpty()) {
          backfillRunner.factory.transacter.transaction { session ->
            val dbRunPartition = session.load(backfillRunner.partitionId)
            dbRunPartition.precomputing_done = true
            updateProgress(dbRunPartition)

            session.save(
              DbEventLog(
                backfillRunner.backfillRunId,
                partition_id = backfillRunner.partitionId,
                type = DbEventLog.Type.STATE_CHANGE,
                message = "precomputing complete"
              )
            )
          }
          logger.info { "Precomputing completed for ${backfillRunner.logLabel()}" }
          break
        }

        pkeyCursor = response.batches.last().batch_range.end
        computedScannedRecordCount += response.batches.sumOf { it.scanned_record_count }
        computedMatchingRecordCount += response.batches.sumOf { it.matching_record_count }

        logger.debug { "Precomputer advanced to $pkeyCursor after scanning ${response.batches}" }
      } catch (e: CancellationException) {
        logger.info(e) { "BatchPrecomputer job cancelled ${backfillRunner.logLabel()}" }
        break
      } catch (e: Exception) {
        logger.info(e) {
          "Rpc failure when precomputing next batch for ${backfillRunner.logLabel()}"
        }
        backfillRunner.onRpcFailure(e, "precomputing batch", stopwatch.elapsed())
      }
    }
    logger.info { "BatchPrecomputer stopped ${backfillRunner.logLabel()}" }
  }

  fun updateProgress(dbRunPartition: DbRunPartition) {
    dbRunPartition.precomputing_pkey_cursor = pkeyCursor
    dbRunPartition.computed_scanned_record_count = computedScannedRecordCount
    dbRunPartition.computed_matching_record_count = computedMatchingRecordCount
  }

  companion object {
    private val logger = getLogger<BatchPrecomputer>()
  }
}
