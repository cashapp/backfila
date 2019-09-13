package app.cash.backfila.client

import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.ConfigureServiceResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface BackfilaApi {
  @POST("/configure_service")
  @Headers(value = [
    "Accept: application/x-protobuf",
    "Content-Type: application/x-protobuf"
  ])
  fun configureService(
    @Body request: ConfigureServiceRequest
  ): Call<ConfigureServiceResponse>
}