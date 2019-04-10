package com.squareup.backfila.client

import com.google.common.util.concurrent.ListenableFuture
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeRequest
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeResponse
import com.squareup.protos.backfila.clientservice.PrepareBackfillRequest
import com.squareup.protos.backfila.clientservice.PrepareBackfillResponse
import com.squareup.protos.backfila.clientservice.RunBatchRequest
import com.squareup.protos.backfila.clientservice.RunBatchResponse

interface BackfilaClientServiceClient {
  fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse

  fun getNextBatchRange(request: GetNextBatchRangeRequest):
      ListenableFuture<GetNextBatchRangeResponse>

  fun runBatch(request: RunBatchRequest): ListenableFuture<RunBatchResponse>
}
