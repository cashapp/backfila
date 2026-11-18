package app.cash.backfila.client

/**
 * Configures connectivity from Backfila to your service.
 */
data class BackfilaClientConfig @JvmOverloads constructor(

  val slack_channel: String?,

  /**
   * Connector information so Backfila knows how to call your service.
   */
  val connector_type: String,

  /**
   * Connector information so Backfila knows how to call your service.
   */
  val connector_extra_data: String,

  /**
   * A service can have multiple variants, each registered independently with
   * Backfila. The default variant is implied if not set.
   */
  val variant: String?,

  /**
   * The service names that may call this service's Backfila callback endpoints. Add a name when
   * your service is deployed where Backfila calls with a different identity.
   */
  val callback_services: List<String> = listOf(DEFAULT_CALLBACK_SERVICE),
) {
  @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
  fun copy(
    slack_channel: String?,
    connector_type: String,
    connector_extra_data: String,
    variant: String?,
  ): BackfilaClientConfig = copy(
    slack_channel = slack_channel,
    connector_type = connector_type,
    connector_extra_data = connector_extra_data,
    variant = variant,
    callback_services = callback_services,
  )

  companion object {
    const val DEFAULT_CALLBACK_SERVICE = "backfila"

    @Deprecated("Binary compatibility", level = DeprecationLevel.HIDDEN)
    @JvmStatic
    @JvmName("copy\$default")
    fun copyDefault(
      self: BackfilaClientConfig,
      slack_channel: String?,
      connector_type: String?,
      connector_extra_data: String?,
      variant: String?,
      mask: Int,
      marker: Any?,
    ): BackfilaClientConfig = self.copy(
      slack_channel = if (mask and 1 != 0) self.slack_channel else slack_channel,
      connector_type = if (mask and 2 != 0) self.connector_type else connector_type!!,
      connector_extra_data = if (mask and 4 != 0) self.connector_extra_data else connector_extra_data!!,
      variant = if (mask and 8 != 0) self.variant else variant,
    )
  }
}
