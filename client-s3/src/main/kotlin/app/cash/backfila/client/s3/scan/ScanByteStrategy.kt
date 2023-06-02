package app.cash.backfila.client.s3.scan

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import com.google.common.base.Stopwatch

/**
 * S3 requires that a file can only be partially read by reading chunks. The semantics of an
 * open-ended read is that you read the rest of the file. This strategy gives you a way to influence
 * how much data you request for each GetNextBatchRange scan request.
 *
 * NOTE: This can contain state for a backfill instance so each backfill should create a copy.
 */
interface ScanByteStrategy {

  fun bytesToScan(): Long

  /**
   * Gives feedback to the strategy on what was accomplished in this GetNextBatchRange scan.
   */
  fun recordResult(
    request: GetNextBatchRangeRequest,
    batches: List<GetNextBatchRangeResponse.Batch>,
    stopwatch: Stopwatch
  )
}