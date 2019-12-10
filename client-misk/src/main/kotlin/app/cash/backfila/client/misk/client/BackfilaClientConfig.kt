package app.cash.backfila.client.misk.client

/**
 * Configures connectivity to the Backfila service.
 */
data class BackfilaClientConfig(
  /** The URL of your service so backfila can call into it. */
  val url: String,

  val slack_channel: String?
)