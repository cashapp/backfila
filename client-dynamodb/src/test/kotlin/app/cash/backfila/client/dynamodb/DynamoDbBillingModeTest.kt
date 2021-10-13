package app.cash.backfila.client.dynamodb

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.model.BillingMode
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput
import com.amazonaws.services.dynamodbv2.model.UpdateTableRequest
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DynamoDbBillingModeTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject lateinit var backfila: Backfila
  @Inject lateinit var db: AmazonDynamoDB

  @Test
  fun `incorrectly configured dynamo fails`() {
    // By default LocalDynamo sets new tables to provisioned
    backfila.createWetRun<EmptyTrackBackfill>()

    // PAY_PER_REQUEST fails
    db.updateTable(
      UpdateTableRequest().withTableName("music_tracks")
        .withBillingMode(BillingMode.PAY_PER_REQUEST).withProvisionedThroughput(
          ProvisionedThroughput()
            .withReadCapacityUnits(2)
            .withWriteCapacityUnits(2)
        )
    )
    assertThatCode {
      backfila.createWetRun<EmptyTrackBackfill>()
    }.hasMessageContaining("billing mode that is not PROVISIONED")

    // Setting it back to provisioned and it works again.
    db.updateTable(
      UpdateTableRequest().withTableName("music_tracks")
        .withBillingMode(BillingMode.PROVISIONED).withProvisionedThroughput(
          ProvisionedThroughput()
            .withReadCapacityUnits(2)
            .withWriteCapacityUnits(2)
        )
    )
    backfila.createWetRun<EmptyTrackBackfill>()
  }

  @Test
  fun `incorrectly configured dynamo does not fail for special backfills`() {
    // PAY_PER_REQUEST passes
    db.updateTable(
      UpdateTableRequest().withTableName("music_tracks")
        .withBillingMode(BillingMode.PAY_PER_REQUEST).withProvisionedThroughput(
          ProvisionedThroughput()
            .withReadCapacityUnits(2)
            .withWriteCapacityUnits(2)
        )
    )
    backfila.createWetRun<ReallyExpensiveBackfill>()

    // And PROVISIONED passes
    db.updateTable(
      UpdateTableRequest().withTableName("music_tracks")
        .withBillingMode(BillingMode.PROVISIONED).withProvisionedThroughput(
          ProvisionedThroughput()
            .withReadCapacityUnits(2)
            .withWriteCapacityUnits(2)
        )
    )
    backfila.createWetRun<ReallyExpensiveBackfill>()
  }

  open class EmptyTrackBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, NoParameters>(dynamoDb) {

    override fun runOne(item: TrackItem, config: BackfillConfig<NoParameters>): Boolean {
      return false
    }
  }

  open class ReallyExpensiveBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, NoParameters>(dynamoDb) {

    // No really this should only be done as a last resort...
    override fun mustHaveProvisionedBillingMode() = false

    override fun runOne(item: TrackItem, config: BackfillConfig<NoParameters>): Boolean {
      return false
    }
  }
}
