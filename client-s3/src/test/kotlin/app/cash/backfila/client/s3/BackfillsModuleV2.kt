package app.cash.backfila.client.s3

import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import misk.inject.KAbstractModule

/**
 * Simulates a Backfills module where all the relevant backfills are registered using V2 backend.
 */
class BackfillsModuleV2 : KAbstractModule() {
  override fun configure() {
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "test.url",
          slack_channel = "#test",
        ),
      ),
    )
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTestV2.BreakfastBackfill>())
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTestV2.BrunchBackfill>())
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTestV2.LunchBackfill>())
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTestV2.DinnerBackfill>())
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTestV2.OptimizedLunchBackfill>())
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTestV2.OptimizedDinnerBackfill>())

    install(S3DatasourceBackfillModule.create<RecordStrategyBackfillTestV2.BrokenBreakfastBackfill>())
  }
}
