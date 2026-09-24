package app.cash.backfila.service

import misk.config.Config
import misk.config.Secret
import misk.jdbc.DataSourceClustersConfig

data class BackfilaConfig(
  val backfill_runner_threads: Int?,
  val data_source_clusters: DataSourceClustersConfig,
  /** Configures Slack notifications; Web API credentials enable threading. */
  val slack: BackfilaSlackConfig?,
  /** Used to construct absolute links to the dashboard, ie. from the Backfila Slackbot. */
  val web_url_root: String,
  /** Sets the minimum number of batches to compute per GetNextBatch call. */
  val minimum_batches_per_get_next_batch_call: Int = 1,
  /** For every thread of a backfill, sets the max number of batches to keep queued. */
  val batch_queue_thread_multiplier: Int = 2,
  /** Support banner shows up on all pages and can point to a Slack channel or other support method, if null banner not shown. */
  val support_button_label: String? = null,
  /** Support banner shows up on all pages and can point to a Slack channel or other support method, if null banner not shown. */
  val support_button_url: String? = null,
) : Config

/** Keeps existing webhook settings at their original paths while allowing Web API credentials. */
data class BackfilaSlackConfig(
  val baseUrl: String = "https://hooks.slack.com/",
  val webhook_path: Secret<String>? = null,
  val default_channel: String? = null,
  val api: misk.slack.webapi.SlackConfig? = null,
) : Config
