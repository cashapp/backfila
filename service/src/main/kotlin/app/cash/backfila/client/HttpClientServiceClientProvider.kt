package app.cash.backfila.client

import com.squareup.moshi.Moshi
import misk.client.HttpClientConfigUrlProvider
import misk.client.HttpClientFactory
import misk.moshi.adapter
import retrofit2.Retrofit
import retrofit2.adapter.guava.GuavaCallAdapterFactory
import retrofit2.converter.wire.WireConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpClientServiceClientProvider @Inject constructor(
  private val httpClientFactory: HttpClientFactory,
  private val httpClientConfigUrlProvider: HttpClientConfigUrlProvider,
  private val moshi: Moshi
) : BackfilaClientServiceClientProvider {
  override fun validateExtraData(connectorExtraData: String?) {
    checkNotNull(connectorExtraData, { "Extra data required for HTTP connector" })
    val fromJson = adapter().fromJson(connectorExtraData)
    checkNotNull(fromJson, { "Failed to parse HTTP connector extra data JSON" })
    checkNotNull(fromJson.url, { "HTTP connector extra data must contain a URL" })
  }

  override fun clientFor(
    serviceName: String,
    connectorExtraData: String?
  ): BackfilaClientServiceClient {
    val url = adapter().fromJson(connectorExtraData!!)!!.url

    val httpClientEndpointConfig = DEFAULT_HTTP_CLIENT_ENDPOINT_CONFIG.copy(
        url = url
    )
    val okHttpClient = httpClientFactory.create(httpClientEndpointConfig)
    val baseUrl = httpClientConfigUrlProvider.getUrl(httpClientEndpointConfig)
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(WireConverterFactory.create())
        .addCallAdapterFactory(GuavaCallAdapterFactory.create())
        .build()
    val api = retrofit.create(HttpClientServiceApi::class.java)
    return HttpClientServiceClient(api)
  }

  private fun adapter() = moshi.adapter<HttpConnectorData>()
}