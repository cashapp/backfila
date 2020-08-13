package app.cash.backfila.client

import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import misk.client.EnvoyClientEndpointProvider
import misk.client.HttpClientEnvoyConfig
import misk.client.HttpClientFactory
import misk.client.HttpClientsConfig
import misk.moshi.adapter
import retrofit2.Retrofit
import retrofit2.adapter.guava.GuavaCallAdapterFactory
import retrofit2.converter.wire.WireConverterFactory
import java.net.URL

@Singleton
class EnvoyClientServiceClientProvider @Inject constructor(
  private val httpClientsConfig: HttpClientsConfig,
  private val httpClientFactory: HttpClientFactory,
  private val moshi: Moshi
) : BackfilaClientServiceClientProvider {
  @com.google.inject.Inject(optional = true)
  lateinit var envoyClientEndpointProvider: EnvoyClientEndpointProvider

  override fun validateExtraData(connectorExtraData: String?) {
    connectorExtraData?.let { adapter().fromJson(connectorExtraData)?.clusterType }
  }

  override fun clientFor(
    serviceName: String,
    connectorExtraData: String?
  ): BackfilaClientServiceClient {
    // If clusterType is specified use it for env, otherwise use null to default to current env.
    val env = connectorExtraData?.let { adapter().fromJson(connectorExtraData)?.clusterType }

    val envoyConfig = HttpClientEnvoyConfig(
        app = serviceName,
        env = env
      )
    val baseUrl = URL(envoyClientEndpointProvider.url(envoyConfig))
    val httpClientEndpointConfig = httpClientsConfig[baseUrl]
    val okHttpClient = httpClientFactory.create(httpClientEndpointConfig)
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
