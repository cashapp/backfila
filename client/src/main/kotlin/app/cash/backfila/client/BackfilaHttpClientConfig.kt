package app.cash.backfila.client

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.jvm.internal.DefaultConstructorMarker

/**
 * Configures connectivity from Backfila to your service.
 *
 * This is a helper config that client implementations can use to make config more readable.
 */
data class BackfilaHttpClientConfig @JvmOverloads constructor(
  /** The URL of your service so backfila can call into it. */
  val url: String,

  val slack_channel: String?,

  val variant: String? = null,

  /** See [BackfilaClientConfig.callback_services]. */
  val callback_services: List<String> = listOf(BackfilaClientConfig.DEFAULT_CALLBACK_SERVICE),
) {
  @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
  constructor(
    url: String,
    slack_channel: String?,
    variant: String?,
    mask: Int,
    marker: DefaultConstructorMarker?,
  ) : this(url, slack_channel, if (mask and 4 != 0) null else variant)

  @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
  fun copy(url: String, slack_channel: String?, variant: String?): BackfilaHttpClientConfig =
    copy(url = url, slack_channel = slack_channel, variant = variant, callback_services = callback_services)

  companion object {
    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    @JvmStatic
    @JvmName("copy\$default")
    fun copyDefault(
      self: BackfilaHttpClientConfig,
      url: String?,
      slack_channel: String?,
      variant: String?,
      mask: Int,
      marker: Any?,
    ): BackfilaHttpClientConfig = self.copy(
      url = if (mask and 1 != 0) self.url else url!!,
      slack_channel = if (mask and 2 != 0) self.slack_channel else slack_channel,
      variant = if (mask and 4 != 0) self.variant else variant,
    )
  }

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
      connector_extra_data = connectorDataAdapter.toJson(httpConnectorData),
      variant,
      callback_services,
    )
  }
}
