package com.squareup.backfila.client

import com.squareup.protos.backfila.service.Connector

interface BackfilaClientServiceClientProvider {
  fun clientFor(serviceName: String, connector: Connector): BackfilaClientServiceClient
}