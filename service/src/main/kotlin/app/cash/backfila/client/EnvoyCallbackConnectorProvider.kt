package app.cash.backfila.client

import app.cash.backfila.client.interceptors.OkHttpClientSpecifiedHeadersInterceptor
import app.cash.backfila.client.interceptors.OkHttpClientSpecifiedHeadersInterceptor.Companion.headersSizeWithinLimit
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
class EnvoyCallbackConnectorProvider @Inject constructor(
  private val httpClientsConfig: HttpClientsConfig,
  private val httpClientFactory: HttpClientFactory,
  private val moshi: Moshi,
) : BackfilaCallbackConnectorProvider {
  @com.google.inject.Inject(optional = true)
  lateinit var envoyClientEndpointProvider: EnvoyClientEndpointProvider

  override fun validateExtraData(connectorExtraData: String?) {
    connectorExtraData?.let {
      val fromJson = adapter().fromJson(connectorExtraData)
      checkNotNull(fromJson) { "Failed to parse HTTP connector extra data JSON" }

      if (!fromJson.headers.isNullOrEmpty()) {
        check(headersSizeWithinLimit(fromJson.headers)) { "Headers too large" }

        for (header in fromJson.headers) {
          checkNotNull(header.name) { "Header names must be set" }
          checkNotNull(header.value) { "Header values must be set" }
        }
      }
    }
  }

  override fun clientFor(
    serviceName: String,
    connectorExtraData: String?,
  ): BackfilaCallbackConnector {
    val extraData = connectorExtraData?.let { adapter().fromJson(connectorExtraData) }
    // If clusterType is specified use it for env, otherwise use null to default to current env.
    var env: String? = extraData?.clusterType
    // If client-specified HTTP headers are specified, honor them.
    var headers: List<HttpHeader>? = extraData?.headers

    val envoyConfig = HttpClientEnvoyConfig(
      app = serviceName,
      env = env,
    )
    val baseUrl = URL(envoyClientEndpointProvider.url(envoyConfig))
    val httpClientEndpointConfig = httpClientsConfig[baseUrl]

    var okHttpClient = httpClientFactory.create(httpClientEndpointConfig)
    if (!headers.isNullOrEmpty()) {
      okHttpClient = okHttpClient.newBuilder()
        .addInterceptor(OkHttpClientSpecifiedHeadersInterceptor(headers))
        .build()
    }

    val retrofit = Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(okHttpClient)
      .addConverterFactory(WireConverterFactory.create())
      .addCallAdapterFactory(GuavaCallAdapterFactory.create())
      .build()
    val api = retrofit.create(EnvoyClientServiceApi::class.java)
    val logData = "envoyConfig: ${httpClientEndpointConfig.envoy}, " +
      "url: ${httpClientEndpointConfig.url}, " +
      "headers: $headers"
    return EnvoyCallbackConnector(api, logData)
  }

  private fun adapter() = moshi.adapter<EnvoyConnectorData>()
}
