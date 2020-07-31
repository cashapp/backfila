package app.cash.backfila.client

import app.cash.backfila.service.HttpClientNetworkInterceptor
import misk.client.HttpClientConfigUrlProvider
import misk.client.HttpClientFactory
import misk.client.HttpClientsConfig
import javax.inject.Inject
import javax.inject.Singleton
import misk.exceptions.BadRequestException
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.adapter.guava.GuavaCallAdapterFactory
import retrofit2.converter.wire.WireConverterFactory

@Singleton
class ClientProvider @Inject constructor(
  private val httpClientsConfig: HttpClientsConfig,
  private val httpClientFactory: HttpClientFactory,
  private val httpClientConfigUrlProvider: HttpClientConfigUrlProvider,
  @HttpClientNetworkInterceptor private val networkInterceptors: List<Interceptor>
) {
  fun clientForUrl(url: String): HttpClientServiceApi {
    val httpClientEndpointConfig = httpClientsConfig[url]
    val okHttpClient = httpClientFactory.create(httpClientEndpointConfig)
        .newBuilder()
        .apply {
          networkInterceptors.forEach {
            addNetworkInterceptor(it)
          }
        }
        .build()
    val baseUrl = httpClientConfigUrlProvider.getUrl(httpClientEndpointConfig)
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(WireConverterFactory.create())
        .addCallAdapterFactory(GuavaCallAdapterFactory.create())
        .build()
    return retrofit.create(HttpClientServiceApi::class.java)
  }
}
