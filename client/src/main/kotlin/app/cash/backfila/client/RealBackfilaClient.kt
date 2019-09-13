package app.cash.backfila.client

import app.cash.backfila.client.Connectors.HTTP
import app.cash.backfila.protos.service.ConfigureServiceRequest
import com.google.common.base.Preconditions.checkState
import com.squareup.moshi.Moshi
import com.squareup.wire.WireJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.io.UncheckedIOException

class RealBackfilaClient(host: String, okHttpClient: OkHttpClient) : BackfilaClient {
  private val backfilaApi: BackfilaApi

  init {
    val moshi = Moshi.Builder()
        .add(WireJsonAdapterFactory())
        .build()
    val retrofit = Retrofit.Builder()
        .baseUrl(host)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(okHttpClient)
        .build()
    this.backfilaApi = retrofit.create(BackfilaApi::class.java)
  }

  override fun configureService() {
    val request = ConfigureServiceRequest.Builder()
        .connector_type(HTTP)
        .backfills(emptyList())
        .build()

    try {
      val response = backfilaApi.configureService(request).execute()
      checkState(response.isSuccessful)
    } catch (e: IOException) {
      throw UncheckedIOException(e)
    }
  }
}
