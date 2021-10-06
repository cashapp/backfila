package app.cash.backfila.client.misk.dynamodb

import app.cash.backfila.client.misk.MiskBackfillModule
import app.cash.backfila.client.BackfilaClientConfig
import misk.dynamodb.DynamoDbService
import misk.inject.KAbstractModule
import misk.inject.toKey

/**
 * Simulates a Backfills module where all the relevant backfills are registered.
 */
class BackfillsModule : KAbstractModule() {
  override fun configure() {
    val dependsOn = listOf(DynamoDbService::class.toKey())
    install(
      MiskBackfillModule(
        BackfilaClientConfig(
          url = "test.url", slack_channel = "#test"
        ),
        dependsOn = dependsOn
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
