package app.cash.backfila.service

import misk.config.Config
import misk.jdbc.DataSourceClustersConfig
import misk.slack.SlackConfig

data class BackfilaConfig(
  val backfill_runner_threads: Int?,
  val data_source_clusters: DataSourceClustersConfig,
  val slack: SlackConfig?,
  val web_url_root: String
) : Config
