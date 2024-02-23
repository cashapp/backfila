package app.cash.backfila.client

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import okio.ByteString.Companion.encodeUtf8

@Singleton
class FakeBackfilaCallbackConnector @Inject constructor() : BackfilaCallbackConnector {
  val prepareBackfillResponses = LinkedList<PrepareBackfillResponse>()

  val getNextBatchRangeRequests = Channel<GetNextBatchRangeRequest>()

  /** Send empty data here to signal GetNextBatchRange should return the next batch. */
  val getNextBatchRangeResponses = Channel<Result<GetNextBatchRangeResponse>>()

  val runBatchRequests = Channel<RunBatchRequest>()

  /** Send responses or exceptions here to return them to the runner. */
  val runBatchResponses = Channel<Result<RunBatchResponse>>()

  fun dontBlockGetNextBatch() {
    getNextBatchRangeRequests.close()
  }

  fun dontBlockRunBatch() {
    runBatchRequests.close()
  }

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    if (prepareBackfillResponses.isNotEmpty()) {
      return prepareBackfillResponses.removeFirst()
    }
    return PrepareBackfillResponse.Builder()
      .partitions(
        listOf(
          PrepareBackfillResponse.Partition(
            "-80",
            KeyRange("0".encodeUtf8(), "1000".encodeUtf8()),
            1_000_000L,
          ),
          PrepareBackfillResponse.Partition(
            "80-",
            KeyRange("0".encodeUtf8(), "1000".encodeUtf8()),
            null,
          ),
        ),
      )
      .build()
  }

  override suspend fun getNextBatchRange(request: GetNextBatchRangeRequest):
    GetNextBatchRangeResponse {
    if (!getNextBatchRangeRequests.isClosedForSend) {
      getNextBatchRangeRequests.send(request)
      return getNextBatchRangeResponses.receive().getOrThrow()
    }
    val nextStart = if (request.previous_end_key != null) {
      request.previous_end_key.utf8().toLong() + 1
    } else {
      request.backfill_range.start.utf8().toLong()
    }
    var nextEnd = nextStart + request.batch_size - 1
    if (nextEnd > request.backfill_range.end.utf8().toLong()) {
      nextEnd = request.backfill_range.end.utf8().toLong()
    }
    if (nextStart > request.backfill_range.end.utf8().toLong()) {
      return GetNextBatchRangeResponse(listOf())
    }
    return GetNextBatchRangeResponse(
      listOf(
        GetNextBatchRangeResponse.Batch(
          KeyRange(nextStart.toString().encodeUtf8(), nextEnd.toString().encodeUtf8()),
          nextEnd - nextStart + 1,
          nextEnd - nextStart + 1,
        ),
      ),
    )
  }

  override suspend fun runBatch(request: RunBatchRequest): RunBatchResponse {
    if (runBatchRequests.isClosedForSend) {
      return RunBatchResponse.Builder().build()
    }
    runBatchRequests.send(request)
    return runBatchResponses.receive().getOrThrow()
  }

  override fun connectionLogData() = "FakeBackfilaClientServiceClient so no connection"
}
