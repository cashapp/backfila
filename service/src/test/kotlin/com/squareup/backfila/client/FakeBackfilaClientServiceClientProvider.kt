package com.squareup.backfila.client

import com.squareup.protos.backfila.service.ServiceType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeBackfilaClientServiceClientProvider @Inject constructor() :
    BackfilaClientServiceClientProvider {
  override fun clientFor(serviceName: String, type: ServiceType): BackfilaClientServiceClient {
    return FakeBackfilaClientServiceClient()
  }
}