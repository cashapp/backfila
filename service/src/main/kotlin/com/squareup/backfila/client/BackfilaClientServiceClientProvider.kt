package com.squareup.backfila.client

import com.squareup.protos.backfila.service.ServiceType

interface BackfilaClientServiceClientProvider {
  fun clientFor(serviceName: String, type: ServiceType): BackfilaClientServiceClient
}