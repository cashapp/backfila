package app.cash.backfila.client.misk

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientsConfig
import misk.client.HttpClientsConfigModule

class Backfill1 : Backfill {
  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    TODO(
        "not implemented") // To change body of created functions use File | Settings | File Templates.
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    TODO(
        "not implemented") // To change body of created functions use File | Settings | File Templates.
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    TODO(
        "not implemented") // To change body of created functions use File | Settings | File Templates.
  }
}

fun main(args: Array<String>) {

  MiskApplication(
      BackfilaClientModule(
          BackfilaClientConfig("#test"),
          listOf(Backfill1::class.java, Backfill::class.java)
      ),
      HttpClientsConfigModule(
          HttpClientsConfig(endpoints = mapOf(
              "backfila" to HttpClientEndpointConfig(url = "http://localhost:8080")))
      ),
      MiskRealServiceModule()
  ).run(args)
}
