package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.embedded.BackfillRun
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import java.util.ArrayDeque
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * A simple backfill run for test and development. Unlike the backfila service, this doesn't use
 * threads, sleeping, or backoff.
 *
 * This is not thread safe.
 */
internal class EmbeddedBackfillRun<B : Backfill<*, *>>(
  private val operator: BackfillOperator<*, *>,
  override val dryRun: Boolean,
  override val parameters: Map<String, ByteString>,
  override val rangeStart: String?,
  override val rangeEnd: String?,

  override var batchSize: Long = 100L,
  override var scanSize: Long = 10_000L,
  override var computeCountLimit: Long = 1L
) : BackfillRun<B> {
  override val backfill: B
    // This api on the operator returns the same backfill it was initialized with.
    @Suppress("UNCHECKED_CAST")
    get() = operator.backfill as B
  override val prepareBackfillResponse: PrepareBackfillResponse

  private val precomputeProgress: Map<String, MutableInstanceCursor>
  override var precomputeMatchingCount: Long = 0L
  override var precomputeScannedCount: Long = 0L

  private val scanProgress: Map<String, MutableInstanceCursor>
  override val instanceProgressSnapshot: Map<String, InstanceCursor>
    get() = scanProgress.mapValues { it.value.snapshot() }

  private val batchesToRun: ArrayDeque<BatchSnapshot> = ArrayDeque()
  override val batchesToRunSnapshot: List<BatchSnapshot>
    get() = batchesToRun.toList()

  /** Prepares a backfill for a run. */
  init {
    prepareBackfillResponse = operator.prepareBackfill(PrepareBackfillRequest.Builder()
        .backfill_name(operator.name())
        .range(KeyRange(rangeStart?.encodeUtf8(), rangeEnd?.encodeUtf8()))
        .parameters(parameters)
        .dry_run(dryRun)
        .build())
    precomputeProgress = prepareBackfillResponse.instances.associate {
      it.instance_name to MutableInstanceCursor(it.instance_name, it.backfill_range)
    }
    scanProgress = prepareBackfillResponse.instances.associate {
      it.instance_name to MutableInstanceCursor(it.instance_name, it.backfill_range)
    }
  }

  override fun precomputeScan(): GetNextBatchRangeResponse {
    val notDone = precomputeProgress.filterNot { it.value.done }
    check(notDone.isNotEmpty()) { "Nothing left to precompute" }
    val cursor = notDone.values.first()
    val response =
        operator.getNextBatchRange(GetNextBatchRangeRequest.Builder()
            .instance_name(cursor.instanceName)
            .backfill_range(cursor.keyRange)
            .previous_end_key(cursor.previousEndKey)
            .parameters(parameters)
            .batch_size(batchSize)
            .scan_size(scanSize)
            .compute_count_limit(computeCountLimit)
            .precomputing(true)
            .dry_run(dryRun)
            .build())
    if (response.batches.isEmpty()) {
      cursor.done = true
    } else {
      cursor.previousEndKey = response.batches.map { it.batch_range.end }.max()
      response.batches.onEach {
        precomputeMatchingCount += it.matching_record_count
        precomputeScannedCount += it.scanned_record_count
      }
    }
    return response
  }

  override fun precomputeRemaining() {
    do {
      precomputeScan()
    } while (!finishedPrecomputing())
  }

  override fun finishedPrecomputing() = precomputeProgress.all { it.value.done }

  override fun instanceScan(instanceName: String): GetNextBatchRangeResponse {
    val cursor = scanProgress[instanceName] ?: error(
        "Instance $instanceName not found. Valid instances are ${scanProgress.keys}")
    val response =
        operator.getNextBatchRange(GetNextBatchRangeRequest.Builder()
            .instance_name(cursor.instanceName)
            .backfill_range(cursor.keyRange)
            .previous_end_key(cursor.previousEndKey)
            .parameters(parameters)
            .batch_size(batchSize)
            .scan_size(scanSize)
            .compute_count_limit(computeCountLimit)
            .dry_run(dryRun)
            .build())
    when (response.batches.isEmpty()) {
      true -> cursor.done = true
      false -> cursor.previousEndKey = response.batches.map { it.batch_range.end }.max()
    }
    batchesToRun.addAll(response.batches
        .filterNot { it.matching_record_count == 0L } // Remove batches that have no matching records.
        .map {
          BatchSnapshot(
              instanceName,
              it.batch_range,
              it.scanned_record_count,
              it.matching_record_count)
        })
    return response
  }

  override fun singleScan(): GetNextBatchRangeResponse {
    val cursor = scanProgress.values.first { !it.done }
    return instanceScan(cursor.instanceName)
  }

  override fun scanRemaining() {
    do {
      singleScan()
    } while (!finishedScanning())
  }

  override fun finishedScanning() = scanProgress.all { it.value.done }

  override fun runBatch() {
    check(!batchesToRun.isEmpty()) { "There must be batches to run" }
    val batch = batchesToRun.remove()
    operator.runBatch(RunBatchRequest.Builder()
        .instance_name(batch.instanceName)
        .batch_range(batch.batchRange)
        .parameters(parameters)
        .dry_run(dryRun)
        .build())
  }

  override fun runAllScanned() {
    while (batchesToRun.isNotEmpty()) {
      runBatch()
    }
  }

  override fun complete() = finishedScanning() && batchesToRun.isEmpty()

  override fun toString() = "BackfillRun[${this.javaClass.toGenericString()}]"
}

private class MutableInstanceCursor(
  val instanceName: String,
  val keyRange: KeyRange
) {
  var previousEndKey: ByteString? = null
  var done: Boolean = false

  fun snapshot() = InstanceCursor(
      instanceName, keyRange, previousEndKey, done)
}

/** Immutable snapshot of a cursor. */
data class InstanceCursor(
  val instanceName: String,
  val keyRange: KeyRange,
  val previousEndKey: ByteString?,
  val done: Boolean
) {
  fun utf8RangeStart() = keyRange.start?.utf8()
  fun utf8RangeEnd() = keyRange.end?.utf8()
  fun utf8PreviousEndKey() = previousEndKey?.utf8()
}

data class BatchSnapshot(
  val instanceName: String,
  val batchRange: KeyRange,
  val scannedRecordCount: Long,
  val matchingRecordCount: Long
) {
  fun utf8RangeStart() = batchRange.start.utf8()
  fun utf8RangeEnd() = batchRange.end.utf8()
}
