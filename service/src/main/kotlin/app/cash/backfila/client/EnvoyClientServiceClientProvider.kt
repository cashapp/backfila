package app.cash.backfila.client

import app.cash.backfila.client.Connectors.ENVOY
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import misk.client.HttpClientConfigUrlProvider
import misk.client.HttpClientEnvoyConfig
import misk.client.HttpClientFactory
import misk.client.HttpClientsConfig
import misk.moshi.adapter
import retrofit2.Retrofit
import retrofit2.adapter.guava.GuavaCallAdapterFactory
import retrofit2.converter.wire.WireConverterFactory

@Singleton
class EnvoyClientServiceClientProvider @Inject constructor(
  private val defaultHttpClientsConfig: HttpClientsConfig,
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
    /*
    url = getUrl()
    val httpClientEndpointConfig = httpClientsConfig[url]
    val okHttpClient = okHttpClient(httpClientEndpointConfig)
    val baseUrl = url

     */

    val httpClientEndpointConfig = defaultHttpClientEndpointConfig.copy(
        envoy = HttpClientEnvoyConfig(
            app = serviceName,
            env = env
        )
    )
//    http://<env>.<serviceName>.gns.square/
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
