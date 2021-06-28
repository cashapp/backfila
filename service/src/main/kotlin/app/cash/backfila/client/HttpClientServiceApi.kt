package app.cash.backfila.client

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// Methods implemented by the client service library that backfila calls out to.
interface HttpClientServiceApi {
  @POST("$BASE_PATH/prepare-backfill")
  @Headers(
    value = [
      "Accept: application/x-protobuf",
      "Content-Type: application/x-protobuf"
    ]
  )
  fun prepareBackfill(
    @Body request: PrepareBackfillRequest
  ): Call<PrepareBackfillResponse>

  @POST("$BASE_PATH/get-next-batch-range")
  @Headers(
    value = [
      "Accept: application/x-protobuf",
      "Content-Type: application/x-protobuf"
    ]
  )
  suspend fun getNextBatchRange(
    @Body request: GetNextBatchRangeRequest
  ): GetNextBatchRangeResponse

  @POST("$BASE_PATH/run-batch")
  @Headers(
    value = [
      "Accept: application/x-protobuf",
      "Content-Type: application/x-protobuf"
    ]
  )
  suspend fun runBatch(
    @Body request: RunBatchRequest
  ): RunBatchResponse

  companion object {
    private const val BASE_PATH = "backfila"
  }
}
