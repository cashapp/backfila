package app.cash.backfila.client

import app.cash.backfila.client.interceptors.OkHttpGnsLabelInterceptor
import com.squareup.moshi.Moshi
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import misk.client.HttpClientFactory
import misk.client.HttpClientsConfig
import misk.moshi.adapter
import retrofit2.Retrofit
import retrofit2.adapter.guava.GuavaCallAdapterFactory
import retrofit2.converter.wire.WireConverterFactory
import wisp.client.EnvoyClientEndpointProvider
import wisp.client.HttpClientEnvoyConfig

@Singleton
class EnvoyClientServiceClientProvider @Inject constructor(
  private val httpClientsConfig: HttpClientsConfig,
  private val httpClientFactory: HttpClientFactory,
  private val moshi: Moshi,
) : BackfilaClientServiceClientProvider {
  @com.google.inject.Inject(optional = true)
  lateinit var envoyClientEndpointProvider: EnvoyClientEndpointProvider

  override fun validateExtraData(connectorExtraData: String?) {
    connectorExtraData?.let { adapter().fromJson(connectorExtraData)?.clusterType }
  }

  override fun clientFor(
    serviceName: String,
    connectorExtraData: String?,
    perBackfillRunOverrideData: PerRunOverrideData?,
  ): BackfilaClientServiceClient {
    var env: String? = perBackfillRunOverrideData?.let { perBackfillRunOverrideData.overrideClusterType }
    var label: String? = null
    if (connectorExtraData != null) {
      val extraData = adapter().fromJson(connectorExtraData)

      // First try to read the env from the per-run overrides
      // If there is no override, try to read the env from the connector extra data.
      // If there is no env specified, use null to default to current env.
      env = env ?: extraData?.clusterType

      // If a GNS label is specified use it, otherwise no label is used.
      label = extraData?.gnsLabel
    }

    val envoyConfig = HttpClientEnvoyConfig(
      app = serviceName,
      env = env,
    )
    val baseUrl = URL(envoyClientEndpointProvider.url(envoyConfig))
    val httpClientEndpointConfig = httpClientsConfig[baseUrl]

    var okHttpClient = httpClientFactory.create(httpClientEndpointConfig)
    if (label != null) {
      okHttpClient = okHttpClient.newBuilder()
        .addInterceptor(OkHttpGnsLabelInterceptor(label!!))
        .build()
    }

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
