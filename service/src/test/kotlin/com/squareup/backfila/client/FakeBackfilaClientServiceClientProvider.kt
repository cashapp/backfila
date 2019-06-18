package com.squareup.backfila.client

import com.squareup.protos.backfila.service.Connector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeBackfilaClientServiceClientProvider @Inject constructor() :
    BackfilaClientServiceClientProvider {
  override fun clientFor(serviceName: String, connector: Connector): BackfilaClientServiceClient {
    return FakeBackfilaClientServiceClient()
  }
}