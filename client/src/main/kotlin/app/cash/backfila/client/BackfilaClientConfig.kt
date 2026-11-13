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
  companion object {
    const val DEFAULT_CALLBACK_SERVICE = "backfila"
  }
}
