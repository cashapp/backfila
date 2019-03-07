package com.squareup.backfila.service

import com.squareup.skim.config.SkimServiceConfig
import misk.client.HttpClientsConfig
import misk.config.Config
import misk.web.WebConfig

data class BackfilaConfig(
  val skim: SkimServiceConfig
) : Config