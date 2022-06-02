package app.cash.backfila.service

import misk.config.Config
import misk.jdbc.DataSourceClustersConfig
import misk.slack.SlackConfig

data class BackfilaConfig(
  val backfill_runner_threads: Int?,
  val data_source_clusters: DataSourceClustersConfig,
  val slack: SlackConfig?,
  val web_url_root: String,
  // Sets the minimum number of batches to compute per GetNextBatch call.
  val minimum_batches_per_get_next_batch_call: Int = 1,
  // For every thread of a backfill, sets the max number of batches to keep queued.
  val batch_queue_thread_multiplier: Int = 2,
) : Config
