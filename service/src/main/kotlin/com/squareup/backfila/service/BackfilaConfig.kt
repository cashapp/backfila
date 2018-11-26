package com.squareup.backfila.service

import misk.client.HttpClientsConfig
import misk.config.Config
import misk.web.WebConfig

data class BackfilaConfig(
  val web: WebConfig,
  val http_clients: HttpClientsConfig
) : Config