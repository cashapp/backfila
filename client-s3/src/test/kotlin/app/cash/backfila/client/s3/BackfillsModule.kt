package app.cash.backfila.client.s3

import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import misk.inject.KAbstractModule

/**
 * Simulates a Backfills module where all the relevant backfills are registered.
 */
class BackfillsModule : KAbstractModule() {
  override fun configure() {
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "test.url",
          slack_channel = "#test",
        ),
      ),
    )
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTest.BreakfastBackfill>())
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTest.BrunchBackfill>())
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTest.LunchBackfill>())
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTest.DinnerBackfill>())
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTest.OptimizedLunchBackfill>())
    install(S3DatasourceBackfillModule.create<S3Utf8StringNewlineBackfillTest.OptimizedDinnerBackfill>())

    install(S3DatasourceBackfillModule.create<RecordStrategyBackfillTest.BrokenBreakfastBackfill>())
  }
}
