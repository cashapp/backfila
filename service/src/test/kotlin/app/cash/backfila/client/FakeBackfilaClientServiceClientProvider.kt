package app.cash.backfila.client

import app.cash.backfila.client.interceptors.OkHttpClientSpecifiedHeadersInterceptor.Companion.headersSizeWithinLimit
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import misk.moshi.adapter

@Singleton
class FakeBackfilaClientServiceClientProvider @Inject constructor(
  private val moshi: Moshi,
) : BackfilaClientServiceClientProvider {
  @Inject lateinit var fakeBackfilaClientServiceClient: FakeBackfilaClientServiceClient

  override fun validateExtraData(connectorExtraData: String?) {
    connectorExtraData?.let {
      val fromJson = moshi.adapter<EnvoyConnectorData>().fromJson(connectorExtraData)
      checkNotNull(fromJson, { "Failed to parse HTTP connector extra data JSON" })

      if (!fromJson.headers.isNullOrEmpty()) {
        check(headersSizeWithinLimit(fromJson.headers)) { "Headers too large" }

        for (header in fromJson.headers) {
          checkNotNull(header.name, { "Header names must be set" })
          checkNotNull(header.value, { "Header values must be set" })
        }
      }
    }
  }

  override fun clientFor(
    serviceName: String,
    connectorExtraData: String?,
  ): BackfilaClientServiceClient {
    return fakeBackfilaClientServiceClient
  }
}
