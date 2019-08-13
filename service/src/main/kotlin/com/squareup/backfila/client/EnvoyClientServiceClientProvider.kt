package com.squareup.backfila.client

import com.squareup.moshi.Moshi
import misk.client.HttpClientConfigUrlProvider
import misk.client.HttpClientEnvoyConfig
import misk.client.HttpClientFactory
import misk.moshi.adapter
import retrofit2.Retrofit
import retrofit2.adapter.guava.GuavaCallAdapterFactory
import retrofit2.converter.wire.WireConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnvoyClientServiceClientProvider @Inject constructor(
  private val httpClientFactory: HttpClientFactory,
  private val httpClientConfigUrlProvider: HttpClientConfigUrlProvider,
  private val moshi: Moshi
) : BackfilaClientServiceClientProvider {
  override fun validateExtraData(connectorExtraData: String?) {
    connectorExtraData?.let { adapter().fromJson(connectorExtraData)?.clusterType }
  }

  override fun clientFor(
    serviceName: String,
    connectorExtraData: String?
  ): BackfilaClientServiceClient {
    // If clusterType is specified use it for env, otherwise use null to default to current env.
    val env = connectorExtraData?.let { adapter().fromJson(connectorExtraData)?.clusterType }

    val httpClientEndpointConfig = DEFAULT_HTTP_CLIENT_ENDPOINT_CONFIG.copy(
        envoy = HttpClientEnvoyConfig(
            app = serviceName,
            env = env
        )
    )
    val okHttpClient = httpClientFactory.create(httpClientEndpointConfig)
    val baseUrl = httpClientConfigUrlProvider.getUrl(httpClientEndpointConfig)
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(WireConverterFactory.create())
        .addCallAdapterFactory(GuavaCallAdapterFactory.create())
        .build()
    val api = retrofit.create(EnvoyClientServiceApi::class.java)
    return EnvoyClientServiceClient(api)
  }

  private fun adapter() = moshi.adapter<EnvoyConnectorData>()
}