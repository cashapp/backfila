package com.squareup.backfila.client

import com.squareup.protos.backfila.clientservice.GetNextBatchRangeRequest
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeResponse
import com.squareup.protos.backfila.clientservice.KeyRange
import com.squareup.protos.backfila.clientservice.PrepareBackfillRequest
import com.squareup.protos.backfila.clientservice.PrepareBackfillResponse
import com.squareup.protos.backfila.clientservice.RunBatchRequest
import com.squareup.protos.backfila.clientservice.RunBatchResponse
import kotlinx.coroutines.channels.Channel
import okio.ByteString.Companion.encodeUtf8
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeBackfilaClientServiceClient @Inject constructor() : BackfilaClientServiceClient {
  val getNextBatchRangeRequests = Channel<GetNextBatchRangeRequest>()
  /** Send empty data here to signal GetNextBatchRange should return the next batch. */
  val getNextBatchRangeResponses = Channel<Result<Unit>>()

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
    return PrepareBackfillResponse(
        listOf(
            PrepareBackfillResponse.Instance(
                "-80",
                KeyRange("0".encodeUtf8(), "1000".encodeUtf8()),
                1_000_000L
            ),
            PrepareBackfillResponse.Instance(
                "80-",
                KeyRange("0".encodeUtf8(), "1000".encodeUtf8()),
                null
            )
        )
    )
  }

  override suspend fun getNextBatchRange(request: GetNextBatchRangeRequest):
      GetNextBatchRangeResponse {
    if (!getNextBatchRangeRequests.isClosedForSend) {
      getNextBatchRangeRequests.send(request)
      getNextBatchRangeResponses.receive().getOrThrow()
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
        listOf(GetNextBatchRangeResponse.Batch(
            KeyRange(nextStart.toString().encodeUtf8(), nextEnd.toString().encodeUtf8()),
            nextEnd - nextStart + 1,
            nextEnd - nextStart + 1
        ))
    )
  }

  override suspend fun runBatch(request: RunBatchRequest): RunBatchResponse {
    if (runBatchRequests.isClosedForSend) {
      return RunBatchResponse.Builder().build()
    }
    runBatchRequests.send(request)
    return runBatchResponses.receive().getOrThrow()
  }
}