package app.cash.backfila.service

import misk.jdbc.DataSourceClustersConfig
import misk.slack.SlackConfig
import wisp.config.Config

data class BackfilaConfig(
  val backfill_runner_threads: Int?,
  val data_source_clusters: DataSourceClustersConfig,
  val slack: SlackConfig?,
  val reminders_enabled: Boolean?,
  val web_url_root: String,
) : Config
