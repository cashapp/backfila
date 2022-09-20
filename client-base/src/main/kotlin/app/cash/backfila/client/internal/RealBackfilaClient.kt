package app.cash.backfila.client.internal

import app.cash.backfila.client.BackfilaApi
import app.cash.backfila.protos.service.CheckBackfillStatusRequest
import app.cash.backfila.protos.service.CheckBackfillStatusResponse
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.ConfigureServiceResponse
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateAndStartBackfillResponse
import java.io.IOException
import java.io.UncheckedIOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealBackfilaClient @Inject internal constructor(
  private val backfilaApi: BackfilaApi
) : BackfilaClient {
  override fun configureService(request: ConfigureServiceRequest): ConfigureServiceResponse {
    try {
      val response = backfilaApi.configureService(request).execute()
      if (!response.isSuccessful) {
        throw IOException("Call failed: ${response.code()} ${response.message()}")
      }
      return response.body()!!
    } catch (e: IOException) {
      throw UncheckedIOException(e)
    }
  }

  override fun createAndStartBackfill(request: CreateAndStartBackfillRequest): CreateAndStartBackfillResponse {
    try {
      val response = backfilaApi.createAndStartbackfill(request).execute()
      if (!response.isSuccessful) {
        throw IOException("Call failed: ${response.code()} ${response.message()}")
      }
      return response.body()!!
    } catch (e: IOException) {
      throw UncheckedIOException(e)
    }
  }

  override fun checkBackfillStatus(request: CheckBackfillStatusRequest): CheckBackfillStatusResponse {
    try {
      val response = backfilaApi.checkBackfillStatus(request).execute()
      if (!response.isSuccessful) {
        throw IOException("Call failed: ${response.code()} ${response.message()}")
      }
      return response.body()!!
    } catch (e: IOException) {
      throw UncheckedIOException(e)
    }
  }
}
