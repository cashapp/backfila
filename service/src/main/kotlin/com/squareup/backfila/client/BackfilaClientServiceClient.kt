package com.squareup.backfila.client

import com.squareup.protos.backfila.clientservice.GetNextBatchRangeRequest
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeResponse
import com.squareup.protos.backfila.clientservice.PrepareBackfillRequest
import com.squareup.protos.backfila.clientservice.PrepareBackfillResponse
import com.squareup.protos.backfila.clientservice.RunBatchRequest
import com.squareup.protos.backfila.clientservice.RunBatchResponse

interface BackfilaClientServiceClient {
  fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse

  suspend fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse

  suspend fun runBatch(request: RunBatchRequest): RunBatchResponse
}
