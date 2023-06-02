package app.cash.backfila.client.s3.scan

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import com.google.common.base.Stopwatch
import java.util.concurrent.TimeUnit

/**
 * The default adaptive strategy that may change over time.
 */
class DefaultAdaptiveScanByteStrategy(initalByteLength: Long) : ScanByteStrategy {
  private var bytesToScan = initalByteLength
  override fun bytesToScan() = bytesToScan

  override fun recordResult(
    request: GetNextBatchRangeRequest,
    batches: List<GetNextBatchRangeResponse.Batch>,
    stopwatch: Stopwatch
  ) {
    // Estimate how many bytes it takes to come up with the correct number of batches.
    val bytesPerBatch = batches.map { it.matching_record_count }.average().toLong()
    val bytesPerScanByCount = bytesPerBatch * request.compute_count_limit

    // Estimate the number of bytes we can process within our time limit.
    val bytesProcessed = batches.map { it.matching_record_count }.sum()
    // Think of ratio of duration as the number of these durations that could fit in our time limit.
    val ratioOfDuration = request.compute_time_limit_ms / stopwatch.elapsed(TimeUnit.MILLISECONDS)
    val bytesPerScanByTime = bytesProcessed * ratioOfDuration

    // Set the result for next time
    bytesToScan = bytesPerScanByCount.coerceAtMost(bytesPerScanByTime)
  }
}