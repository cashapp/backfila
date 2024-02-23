package app.cash.backfila.client

import app.cash.backfila.protos.clientservice.BackfilaClientServiceClient
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import retrofit2.HttpException
import retrofit2.Response

internal class GrpcCallbackConnector internal constructor(
  private val api: BackfilaClientServiceClient,
  private val connectionLogData: String,
) : BackfilaCallbackConnector {

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    return api.PrepareBackfill().executeBlocking(request)
  }

  override suspend fun getNextBatchRange(request: GetNextBatchRangeRequest):
    GetNextBatchRangeResponse {
    return api.GetNextBatchRange().execute(request)
  }

  override suspend fun runBatch(request: RunBatchRequest): RunBatchResponse {
    return api.RunBatch().execute(request)
  }

  override fun connectionLogData() = connectionLogData

  private fun <T> Response<T>.getOrThrow(): T {
    // I should test what happens in a non-200
    if (!this.isSuccessful) {
      throw HttpException(this)
    }
    return this.body()!!
  }
}
