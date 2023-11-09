package app.cash.backfila.client.dynamodb

import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
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
        BackfilaHttpClientConfig(
          url = "test.url", slack_channel = "#test", variant = null,
        ),
        dependsOn = dependsOn,
      ),
    )
    install(DynamoDbBackfillModule.create<DynamoDbBackfillTest.MakeTracksExplicitBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbFilteringTest.FilteredMakeTracksExplicitBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbFilteringTest.DynamoFilterMakeTracksExplicitBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbFilteringTest.DynamoFilterWithAttributeNameMakeTracksExplicitBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbSegmentingTest.SegmentingBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbLastEvaluatedKeyTest.PausingBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbBillingModeTest.EmptyTrackBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbBillingModeTest.ReallyExpensiveBackfill>())
    install(DynamoDbBackfillModule.create<DynamoDbIndexTest.MakeTracksAsSinglesBackfill>())
  }
}
