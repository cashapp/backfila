package com.squareup.backfila.client

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeRequest
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeResponse
import com.squareup.protos.backfila.clientservice.KeyRange
import com.squareup.protos.backfila.clientservice.PrepareBackfillRequest
import com.squareup.protos.backfila.clientservice.PrepareBackfillResponse
import com.squareup.protos.backfila.clientservice.RunBatchRequest
import com.squareup.protos.backfila.clientservice.RunBatchResponse
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

internal class FakeBackfilaClientServiceClient : BackfilaClientServiceClient {
  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    return PrepareBackfillResponse(
        listOf(
            PrepareBackfillResponse.Instance(
                "-80",
                KeyRange(
                    ByteString.of(*"0".toByteArray()),
                    ByteString.of(*"1000".toByteArray())
                ),
                1_000_000L
            ),
            PrepareBackfillResponse.Instance(
                "80-",
                KeyRange(
                    ByteString.of(*"0".toByteArray()),
                    ByteString.of(*"1000".toByteArray())
                ),
                null
            )
        )
    )
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest):
      ListenableFuture<GetNextBatchRangeResponse> {
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
      return Futures.immediateFuture(GetNextBatchRangeResponse(listOf()))
    }
    return Futures.immediateFuture(GetNextBatchRangeResponse(
        listOf(GetNextBatchRangeResponse.Batch(
            KeyRange(nextStart.toString().encodeUtf8(), nextEnd.toString().encodeUtf8()),
            request.batch_size,
            request.batch_size
        ))
    ))
  }

  override fun runBatch(request: RunBatchRequest): ListenableFuture<RunBatchResponse> {
    println(request)
    return Futures.immediateFuture(RunBatchResponse(0L, null))
  }
}