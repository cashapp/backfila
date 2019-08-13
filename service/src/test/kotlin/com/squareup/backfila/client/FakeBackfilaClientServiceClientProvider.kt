package com.squareup.backfila.client

import com.squareup.moshi.Moshi
import misk.moshi.adapter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeBackfilaClientServiceClientProvider @Inject constructor(
  private val moshi: Moshi
) : BackfilaClientServiceClientProvider {
  @Inject lateinit var fakeBackfilaClientServiceClient: FakeBackfilaClientServiceClient

  override fun validateExtraData(connectorExtraData: String?) {
    connectorExtraData?.let { moshi.adapter<EnvoyConnectorData>().fromJson(connectorExtraData) }
  }

  override fun clientFor(
    serviceName: String,
    connectorExtraData: String?
  ): BackfilaClientServiceClient {
    return fakeBackfilaClientServiceClient
  }
}