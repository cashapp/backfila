package app.cash.backfila.client

import app.cash.backfila.client.interceptors.OkHttpClientSpecifiedHeadersInterceptor
import app.cash.backfila.client.interceptors.OkHttpClientSpecifiedHeadersInterceptor.Companion.headersSizeWithinLimit
import app.cash.backfila.protos.clientservice.BackfilaClientServiceClient
import com.squareup.moshi.Moshi
import com.squareup.wire.GrpcClient
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import misk.client.HttpClientConfigUrlProvider
import misk.client.HttpClientFactory
import misk.client.HttpClientsConfig
import misk.moshi.adapter
import okhttp3.Protocol

@Singleton
class GrpcCallbackConnectorProvider @Inject constructor(
  private val httpClientsConfig: HttpClientsConfig,
  private val httpClientFactory: HttpClientFactory,
  private val httpClientConfigUrlProvider: HttpClientConfigUrlProvider,
  private val moshi: Moshi,
) : BackfilaCallbackConnectorProvider {

  override fun validateExtraData(connectorExtraData: String?) {
    connectorExtraData.let {
      checkNotNull(connectorExtraData) { "Extra data required for GRPC connector" }
      val fromJson = adapter().fromJson(connectorExtraData)
      checkNotNull(fromJson) { "Failed to parse GRPC connector extra data JSON" }
      checkNotNull(fromJson.url) { "GRPC connector extra data must contain a URL" }

      if (fromJson.headers.isNotEmpty()) {
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
    val extraData = connectorExtraData.let { adapter().fromJson(connectorExtraData) }
    val url = URL(extraData!!.url)
    // If client-specified HTTP headers are specified, honor them.
    val headers: List<HttpHeader>? = extraData!!.headers

    val httpClientEndpointConfig = httpClientsConfig[url]
    val baseUrl = httpClientConfigUrlProvider.getUrl(httpClientEndpointConfig)

    val okHttpClientBuilder = httpClientFactory.create(httpClientEndpointConfig).newBuilder()
    if (!headers.isNullOrEmpty()) {
      okHttpClientBuilder.addInterceptor(OkHttpClientSpecifiedHeadersInterceptor(headers))
    }

    // Since gRPC uses HTTP/2, force h2c when calling an unencrypted endpoint
    if (baseUrl.startsWith("http://")) {
      okHttpClientBuilder.protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
    } else {
      okHttpClientBuilder.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
    }

    val grpcClient = GrpcClient.Builder()
      .client(okHttpClientBuilder.build())
      .baseUrl(baseUrl)
      .build()
    val api = grpcClient.create(BackfilaClientServiceClient::class)
    val logData = "grpcConfig: ${httpClientEndpointConfig.url}, " +
      "url: ${httpClientEndpointConfig.url}, " +
      "headers: $headers"
    return GrpcCallbackConnector(api, logData)
  }

  private fun adapter() = moshi.adapter<GrpcConnectorData>()
}
