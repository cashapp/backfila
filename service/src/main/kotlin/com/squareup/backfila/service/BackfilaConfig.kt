package com.squareup.backfila.service

import com.squareup.skim.config.SkimServiceConfig
import misk.config.Config
import misk.jdbc.DataSourceClustersConfig

data class BackfilaConfig(
  val skim: SkimServiceConfig,
  val data_source_clusters: DataSourceClustersConfig
) : Config