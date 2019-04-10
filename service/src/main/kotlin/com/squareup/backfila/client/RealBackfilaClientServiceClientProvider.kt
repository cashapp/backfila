package com.squareup.backfila.client

import com.squareup.protos.backfila.service.ServiceType
import misk.client.HttpClientConfigUrlProvider
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientEnvoyConfig
import misk.client.HttpClientFactory
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealBackfilaClientServiceClientProvider @Inject constructor(
  private val httpClientFactory: HttpClientFactory,
  private val httpClientConfigUrlProvider: HttpClientConfigUrlProvider
) : BackfilaClientServiceClientProvider{
  override fun clientFor(serviceName: String, type: ServiceType): BackfilaClientServiceClient {
    // TODO http client caching
    // TODO use type
    val httpClientEndpointConfig = HttpClientEndpointConfig(
        envoy = HttpClientEnvoyConfig(app = serviceName))
    val okHttpClient = httpClientFactory.create(httpClientEndpointConfig)
    val baseUrl = httpClientConfigUrlProvider.getUrl(httpClientEndpointConfig)
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
//        .addCallAdapterFactory(GuavaCallAdapterFactory())
        .build()
    val api = retrofit.create(BackfilaClientServiceSquareDcApi::class.java)
    return SqDcBackfilaClientServiceClient(api)
  }
}