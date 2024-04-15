package app.cash.backfila.client.misk

import app.cash.backfila.client.dynamodbv2.BackfillsModule
import app.cash.backfila.client.dynamodbv2.TrackItem
import app.cash.backfila.embedded.EmbeddedBackfilaModule
import com.google.inject.Provides
import javax.inject.Singleton
import misk.MiskTestingServiceModule
import misk.aws2.dynamodb.testing.DynamoDbTable
import misk.aws2.dynamodb.testing.InProcessDynamoDbModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.EnhancedGlobalSecondaryIndex
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.ProjectionType
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput

/**
 * Simulates a specific service implementation module
 */
class TestingModule : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(wisp.deployment.TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())
    install(BackfillsModule())

    install(EmbeddedBackfilaModule())

    install(
      InProcessDynamoDbModule(
        DynamoDbTable(TrackItem.TABLE_NAME, TrackItem::class) { table ->
          val provisionedThroughput = ProvisionedThroughput.builder()
            .readCapacityUnits(100)
            .writeCapacityUnits(100)
            .build()
          table.globalSecondaryIndices(
            EnhancedGlobalSecondaryIndex.builder()
              .indexName("trackTitleIndex")
              .projection { it.projectionType(ProjectionType.KEYS_ONLY) }
              .provisionedThroughput(provisionedThroughput)
              .build(),
          )
          table
        },
      ),
    )
  }

  @Provides
  @Singleton
  internal fun dynamoDbEnhancedClient(dynamoDbClient: DynamoDbClient): DynamoDbEnhancedClient {
    return DynamoDbEnhancedClient.builder()
      .dynamoDbClient(dynamoDbClient)
      .build()
  }

  @Provides
  @Singleton
  internal fun trackItemDb(dynamoDbClient: DynamoDbEnhancedClient): software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable<TrackItem> {
    return dynamoDbClient.table(TrackItem.TABLE_NAME, TableSchema.fromClass(TrackItem::class.java))
  }
}
