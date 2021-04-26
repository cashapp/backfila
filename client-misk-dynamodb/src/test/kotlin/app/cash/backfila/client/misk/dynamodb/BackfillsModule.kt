package app.cash.backfila.client.misk.dynamodb

import app.cash.backfila.client.misk.BackfillModule
import app.cash.backfila.client.misk.client.BackfilaClientConfig
import misk.inject.KAbstractModule

/**
 * Simulates a Backfills module where all the relevant backfills are registered.
 */
class BackfillsModule : KAbstractModule() {
  override fun configure() {
    install(
      BackfillModule(
        BackfilaClientConfig(
          url = "test.url", slack_channel = "#test"
        )
      )
    )
    install(DynamoDbBackfillModule.create<DynamoDbBackfillTest.MakeTracksExplicitBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbFilteringTest.FilteredMakeTracksExplicitBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbFilteringTest.DynamoFilterMakeTracksExplicitBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbSegmentingTest.SegmentingBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbLastEvaluatedKeyTest.PausingBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbBillingModeTest.EmptyTrackBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbBillingModeTest.ReallyExpensiveBackfill>())
  }
}
