package com.squareup.backfila.client

import com.squareup.protos.backfila.clientservice.GetNextBatchRangeRequest
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeResponse
import com.squareup.protos.backfila.clientservice.PrepareBackfillRequest
import com.squareup.protos.backfila.clientservice.PrepareBackfillResponse
import com.squareup.protos.backfila.clientservice.RunBatchRequest
import com.squareup.protos.backfila.clientservice.RunBatchResponse
import retrofit2.HttpException
import retrofit2.Response

internal class EnvoyClientServiceClient internal constructor(
  private val api: EnvoyClientServiceApi
) : BackfilaClientServiceClient {

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    return api.prepareBackfill(request).execute().getOrThrow()
  }

  override suspend fun getNextBatchRange(request: GetNextBatchRangeRequest):
      GetNextBatchRangeResponse {
    return api.getNextBatchRange(request)
  }

  override suspend fun runBatch(request: RunBatchRequest): RunBatchResponse {
    return api.runBatch(request)
  }

  private fun <T> Response<T>.getOrThrow(): T {
    if (!this.isSuccessful) {
      throw HttpException(this)
    }
    return this.body()!!
  }
}