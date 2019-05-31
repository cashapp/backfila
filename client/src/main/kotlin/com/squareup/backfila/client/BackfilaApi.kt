package com.squareup.backfila.client

import com.squareup.protos.backfila.service.ConfigureServiceRequest
import com.squareup.protos.backfila.service.ConfigureServiceResponse
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