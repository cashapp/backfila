package com.squareup.backfila.client

import com.google.common.util.concurrent.ListenableFuture
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeRequest
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeResponse
import com.squareup.protos.backfila.clientservice.PrepareBackfillRequest
import com.squareup.protos.backfila.clientservice.PrepareBackfillResponse
import com.squareup.protos.backfila.clientservice.RunBatchRequest
import com.squareup.protos.backfila.clientservice.RunBatchResponse
import retrofit2.HttpException
import retrofit2.Response

internal class SqDcBackfilaClientServiceClient internal constructor(
  private val api: BackfilaClientServiceSquareDcApi
) : BackfilaClientServiceClient {

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    return api.prepareBackfill(request).execute().getOrThrow()
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest):
      ListenableFuture<GetNextBatchRangeResponse> {
    TODO("not implemented")
  }

  override fun runBatch(request: RunBatchRequest):
      ListenableFuture<RunBatchResponse> {
    TODO("not implemented")
  }

  private fun <T> Response<T>.getOrThrow(): T {
    if (!this.isSuccessful) {
      throw HttpException(this)
    }
    return this.body()!!
  }
}