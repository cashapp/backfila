package app.cash.backfila.client.dynamodbv2

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput
import software.amazon.awssdk.services.dynamodb.model.UpdateTableRequest
import javax.inject.Inject

@MiskTest(startService = true)
class DynamoDbBillingModeTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject
  lateinit var backfila: Backfila
  @Inject
  lateinit var db: DynamoDbClient

  @Test
  fun `incorrectly configured dynamo fails`() {
    // By default LocalDynamo sets new tables to provisioned
    backfila.createWetRun<EmptyTrackBackfill>()

    // PAY_PER_REQUEST fails
    db.updateTable(
      UpdateTableRequest.builder()
        .tableName("music_tracks")
        .billingMode(BillingMode.PAY_PER_REQUEST)
        .provisionedThroughput(
          ProvisionedThroughput.builder()
            .readCapacityUnits(2)
            .writeCapacityUnits(2)
            .build()
        ).build()
    )
    assertThatCode {
      backfila.createWetRun<EmptyTrackBackfill>()
    }.hasMessageContaining("billing mode that is not PROVISIONED")

    // Setting it back to provisioned and it works again.
    db.updateTable(
      UpdateTableRequest.builder()
        .tableName("music_tracks")
        .billingMode(BillingMode.PROVISIONED)
        .provisionedThroughput(
          ProvisionedThroughput.builder()
            .readCapacityUnits(2)
            .writeCapacityUnits(2)
            .build()
        ).build()
    )
    backfila.createWetRun<EmptyTrackBackfill>()
  }

  @Test
  fun `incorrectly configured dynamo does not fail for special backfills`() {
    // PAY_PER_REQUEST passes
    db.updateTable(
      UpdateTableRequest.builder()
        .tableName("music_tracks")
        .billingMode(BillingMode.PAY_PER_REQUEST)
        .provisionedThroughput(
          ProvisionedThroughput.builder()
            .readCapacityUnits(2)
            .writeCapacityUnits(2)
            .build()
        ).build()
    )
    backfila.createWetRun<ReallyExpensiveBackfill>()

    // And PROVISIONED passes
    db.updateTable(
      UpdateTableRequest.builder()
        .tableName("music_tracks")
        .billingMode(BillingMode.PROVISIONED)
        .provisionedThroughput(
          ProvisionedThroughput.builder()
            .readCapacityUnits(2)
            .writeCapacityUnits(2)
            .build()
        ).build()
    )
    backfila.createWetRun<ReallyExpensiveBackfill>()
  }

  open class EmptyTrackBackfill @Inject constructor(
    dynamoDbClient: DynamoDbClient,
    private val dynamoDbEnhancedClient: DynamoDbEnhancedClient,
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, NoParameters>(dynamoDbClient) {

    override fun runOne(item: TrackItem, config: BackfillConfig<NoParameters>): Boolean {
      return false
    }

    override fun dynamoDbTable(): DynamoDbTable<TrackItem> {
      return dynamoDbEnhancedClient.table(
        TrackItem.TABLE_NAME,
        TableSchema.fromClass(TrackItem::class.java)
      )
    }
  }

  open class ReallyExpensiveBackfill @Inject constructor(
    dynamoDbClient: DynamoDbClient,
    private val dynamoDbEnhancedClient: DynamoDbEnhancedClient,
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, NoParameters>(dynamoDbClient) {

    // No really this should only be done as a last resort...
    override fun mustHaveProvisionedBillingMode() = false

    override fun runOne(item: TrackItem, config: BackfillConfig<NoParameters>): Boolean {
      return false
    }

    override fun dynamoDbTable(): DynamoDbTable<TrackItem> {
      return dynamoDbEnhancedClient.table(
        TrackItem.TABLE_NAME,
        TableSchema.fromClass(TrackItem::class.java)
      )
    }
  }
}
