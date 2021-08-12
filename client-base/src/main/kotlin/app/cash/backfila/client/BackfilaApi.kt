package app.cash.backfila.client

import app.cash.backfila.protos.service.CheckBackfillStatusRequest
import app.cash.backfila.protos.service.CheckBackfillStatusResponse
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.ConfigureServiceResponse
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateAndStartBackfillResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface BackfilaApi {
  @POST("/configure_service")
  @Headers(
    value = [
      "Accept: application/x-protobuf",
      "Content-Type: application/x-protobuf"
    ]
  )
  fun configureService(
    @Body request: ConfigureServiceRequest
  ): Call<ConfigureServiceResponse>

  @POST("/create-and-start-backfill")
  @Headers(
    value = [
      "Accept: application/x-protobuf",
      "Content-Type: application/x-protobuf"
    ]
  )
  fun createAndStartbackfill(
    @Body request: CreateAndStartBackfillRequest
  ): Call<CreateAndStartBackfillResponse>

  @POST("/check-backfill-status")
  @Headers(
    value = [
      "Accept: application/x-protobuf",
      "Content-Type: application/x-protobuf"
    ]
  )
  fun checkBackfillStatus(
    @Body request: CheckBackfillStatusRequest
  ): Call<CheckBackfillStatusResponse>
}
