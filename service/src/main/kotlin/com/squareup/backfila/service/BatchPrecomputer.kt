package com.squareup.backfila.service

import com.squareup.protos.backfila.clientservice.GetNextBatchRangeRequest
import com.squareup.protos.backfila.clientservice.KeyRange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.hibernate.load
import misk.logging.getLogger

class BatchPrecomputer(
  private val backfillRunner: BackfillRunner
) {
  fun run(coroutineScope: CoroutineScope) = coroutineScope.launch {
    logger.info { "BatchPrecomputer started ${backfillRunner.logLabel()}" }

    // Start at the cursor we have in the DB, but after that we need to maintain our own,
    // since the DB stores how far we've completed batches, and we are likely ahead of that.
    var pkeyCursor = backfillRunner.metadata.precomputingPkeyCursor

    while (true) {
      // Use the latest metadata snapshot.
      val metadata = backfillRunner.metadata

      if (metadata.precomputingDone) {
        break
      }

      if (backfillRunner.backingOff()) {
        val backoffMs = backfillRunner.backoffMs()
        logger.info { "BatchPrecomputer ${backfillRunner.logLabel()} backing off for $backoffMs" }
        delay(backoffMs)
      }

      try {
        val computeTimeLimitMs = 5_000L // half of HTTP timeout
        // Just give us a ton of batches!
        val computeCountLimit = 100L
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
            computeCountLimit
        ))

        backfillRunner.onRpcSuccess()

        if (response.batches.isEmpty()) {
          backfillRunner.factory.transacter.transaction { session ->
            val dbRunInstance = session.load(backfillRunner.instanceId)
            dbRunInstance.precomputing_done = true
          }
          break
        }

        backfillRunner.factory.transacter.transaction { session ->
          val dbRunInstance = session.load(backfillRunner.instanceId)
          for (batch in response.batches) {
            pkeyCursor = batch.batch_range.end
            dbRunInstance.computed_scanned_record_count += batch.scanned_record_count
            dbRunInstance.computed_matching_record_count += batch.matching_record_count
          }
          dbRunInstance.precomputing_pkey_cursor = pkeyCursor
          logger.debug { "Precomputer advanced to $pkeyCursor after scanning ${response.batches}" }
        }
      } catch (e: CancellationException) {
        logger.info(e) { "BatchPrecomputer job cancelled ${backfillRunner.logLabel()}" }
        break
      } catch (e: Exception) {
        logger.info(e) {
          "Rpc failure when precomputing next batch for ${backfillRunner.logLabel()}"
        }
        backfillRunner.onRpcFailure()
      }
    }
    logger.info { "BatchPrecomputer stopped ${backfillRunner.logLabel()}" }
  }

  companion object {
    private val logger = getLogger<BatchPrecomputer>()
  }
}