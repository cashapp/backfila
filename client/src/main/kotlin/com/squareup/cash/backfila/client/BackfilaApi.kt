package com.squareup.cash.backfila.client

import com.squareup.protos.cash.backfila.service.ConfigureServiceRequest
import com.squareup.protos.cash.backfila.service.ConfigureServiceResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * This class abstracts away the actual mechanism used for transport, so clients only need to build
 * Request objects and send them away.
 */
interface BackfilaApi {
  @POST("/configure_service")
  @Headers(value = [
    "accept: application/json"
  ])
  fun configureService(
    @Body request: ConfigureServiceRequest
  ): Call<ConfigureServiceResponse>
}