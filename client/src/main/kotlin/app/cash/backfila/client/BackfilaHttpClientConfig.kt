package app.cash.backfila.client

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Configures connectivity from Backfila to your service.
 *
 * This is a helper config that client implementations can use to make config more readable.
 */
data class BackfilaHttpClientConfig(
  /** The URL of your service so backfila can call into it. */
  val url: String,

  val slack_channel: String?,
) {
  fun toBackfilaClientConfig(): BackfilaClientConfig {
    // Creating a local moshi to do the quick config conversion.
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    val connectorDataAdapter = moshi.adapter(HttpConnectorData::class.java)
    val httpConnectorData = HttpConnectorData(url)
    return BackfilaClientConfig(
      slack_channel,
      connector_type = Connectors.HTTP,
      connector_extra_data = connectorDataAdapter.toJson(httpConnectorData)
    )
  }
}
