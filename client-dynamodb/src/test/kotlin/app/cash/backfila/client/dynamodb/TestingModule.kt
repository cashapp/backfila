package app.cash.backfila.client.misk

import app.cash.backfila.client.dynamodb.BackfillsModule
import app.cash.backfila.client.dynamodb.TrackItem
import app.cash.backfila.embedded.EmbeddedBackfilaModule
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.MiskTestingServiceModule
import misk.aws.dynamodb.testing.DynamoDbTable
import misk.aws.dynamodb.testing.InProcessDynamoDbModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule

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

    install(InProcessDynamoDbModule(DynamoDbTable(TrackItem::class)))
  }

  @Provides @Singleton
  fun provideDynamoDbMapper(amazonDynamoDB: AmazonDynamoDB): DynamoDBMapper {
    return DynamoDBMapper(amazonDynamoDB)
  }
}
