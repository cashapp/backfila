package app.cash.backfila.client

/**
 * Configures connectivity from Backfila to your service.
 */
data class BackfilaClientConfig(

  val slack_channel: String?,

  /**
   * Connector information so Backfila knows how to call your service.
   */
  val connector_type: String,

  /**
   * Connector information so Backfila knows how to call your service.
   */
  val connector_extra_data: String,
)
