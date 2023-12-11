package app.cash.backfila.service

import misk.config.Config
import misk.jdbc.DataSourceClustersConfig
import misk.slack.SlackConfig

data class BackfilaConfig(
  val backfill_runner_threads: Int?,
  val data_source_clusters: DataSourceClustersConfig,
  /** Configures Slack API for Backfila Slackbot to notify on backfill status changes. */
  val slack: SlackConfig?,
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
