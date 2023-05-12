package app.cash.backfila.client.s3.scan

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import com.google.common.base.Stopwatch

/**
 * Use this if all you want is always read a certain number of bytes. Use this if the adaptive
 * strategy isn't working for you.
 */
class FixedScanByteStrategy(private val fixedByteLength: Long) : ScanByteStrategy {
  override fun bytesToScan() = fixedByteLength

  override fun recordResult(
    request: GetNextBatchRangeRequest,
    batches: List<GetNextBatchRangeResponse.Batch>,
    stopwatch: Stopwatch
  ) {
    // Do nothing.
  }
}