package com.squareup.backfila.client

import com.squareup.protos.backfila.service.Connector
import misk.client.HttpClientConfigUrlProvider
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientEnvoyConfig
import misk.client.HttpClientFactory
import retrofit2.Retrofit
import retrofit2.adapter.guava.GuavaCallAdapterFactory
import retrofit2.converter.wire.WireConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealBackfilaClientServiceClientProvider @Inject constructor(
  private val httpClientFactory: HttpClientFactory,
  private val httpClientConfigUrlProvider: HttpClientConfigUrlProvider
) : BackfilaClientServiceClientProvider {
  override fun clientFor(serviceName: String, connector: Connector): BackfilaClientServiceClient {
    // TODO http client caching
    // TODO use connector type
    // TODO use typedhttpclient?
    // TODO timeout
    val httpClientEndpointConfig = HttpClientEndpointConfig(
        envoy = HttpClientEnvoyConfig(app = serviceName))
    val okHttpClient = httpClientFactory.create(httpClientEndpointConfig)
    val baseUrl = httpClientConfigUrlProvider.getUrl(httpClientEndpointConfig)
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(WireConverterFactory.create())
        .addCallAdapterFactory(GuavaCallAdapterFactory.create())
        .build()
    val api = retrofit.create(BackfilaClientServiceSquareDcApi::class.java)
    return SqDcBackfilaClientServiceClient(api)
  }
}