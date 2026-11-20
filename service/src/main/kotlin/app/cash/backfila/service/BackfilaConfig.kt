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
  /** Configures the optional MCP server. See [BackfilaMcpConfig]. */
  val mcp: BackfilaMcpConfig = BackfilaMcpConfig(),
) : Config {
  @Deprecated("Kept for binary compatibility with callers compiled before `mcp` was added.", level = DeprecationLevel.HIDDEN)
  constructor(
    backfill_runner_threads: Int?,
    data_source_clusters: DataSourceClustersConfig,
    slack: SlackConfig?,
    web_url_root: String,
    minimum_batches_per_get_next_batch_call: Int = 1,
    batch_queue_thread_multiplier: Int = 2,
    support_button_label: String? = null,
    support_button_url: String? = null,
  ) : this(
    backfill_runner_threads,
    data_source_clusters,
    slack,
    web_url_root,
    minimum_batches_per_get_next_batch_call,
    batch_queue_thread_multiplier,
    support_button_label,
    support_button_url,
    BackfilaMcpConfig(),
  )

  @Deprecated("Kept for binary compatibility with callers compiled before `mcp` was added.", level = DeprecationLevel.HIDDEN)
  fun copy(
    backfill_runner_threads: Int? = this.backfill_runner_threads,
    data_source_clusters: DataSourceClustersConfig = this.data_source_clusters,
    slack: SlackConfig? = this.slack,
    web_url_root: String = this.web_url_root,
    minimum_batches_per_get_next_batch_call: Int = this.minimum_batches_per_get_next_batch_call,
    batch_queue_thread_multiplier: Int = this.batch_queue_thread_multiplier,
    support_button_label: String? = this.support_button_label,
    support_button_url: String? = this.support_button_url,
  ): BackfilaConfig = copy(
    backfill_runner_threads = backfill_runner_threads,
    data_source_clusters = data_source_clusters,
    slack = slack,
    web_url_root = web_url_root,
    minimum_batches_per_get_next_batch_call = minimum_batches_per_get_next_batch_call,
    batch_queue_thread_multiplier = batch_queue_thread_multiplier,
    support_button_label = support_button_label,
    support_button_url = support_button_url,
    mcp = mcp,
  )
}
