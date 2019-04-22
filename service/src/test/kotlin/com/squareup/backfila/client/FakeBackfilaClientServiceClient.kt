package com.squareup.backfila.client

import com.google.common.util.concurrent.ListenableFuture
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeRequest
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeResponse
import com.squareup.protos.backfila.clientservice.KeyRange
import com.squareup.protos.backfila.clientservice.PrepareBackfillRequest
import com.squareup.protos.backfila.clientservice.PrepareBackfillResponse
import com.squareup.protos.backfila.clientservice.RunBatchRequest
import com.squareup.protos.backfila.clientservice.RunBatchResponse
import okio.ByteString

internal class FakeBackfilaClientServiceClient : BackfilaClientServiceClient {
  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    return PrepareBackfillResponse(
        listOf(
            PrepareBackfillResponse.Instance(
                "-80",
                KeyRange(
                    ByteString.of(*"1".toByteArray()),
                    ByteString.of(*"10000".toByteArray())
                ),
                1_000_000L
            ),
            PrepareBackfillResponse.Instance(
                "80-",
                KeyRange(
                    ByteString.of(*"1".toByteArray()),
                    ByteString.of(*"10000".toByteArray())
                ),
                null
            )
        )
    )
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest):
      ListenableFuture<GetNextBatchRangeResponse> {
    TODO(
        "not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun runBatch(request: RunBatchRequest): ListenableFuture<RunBatchResponse> {
    TODO(
        "not implemented") //To change body of created functions use File | Settings | File Templates.
  }
}