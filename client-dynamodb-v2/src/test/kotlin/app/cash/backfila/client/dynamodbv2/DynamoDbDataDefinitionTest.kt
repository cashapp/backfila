package app.cash.backfila.client.dynamodbv2

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

@MiskTest(startService = true)
class DynamoDbDataDefinitionTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject
  lateinit var backfila: Backfila

  @Inject
  lateinit var testData: TrackData

  @Test
  fun `dataDefinition filter expression is used`() {
    testData.addAllTheMusic()

    val run = backfila.createWetRun<DataDefinitionFilterBackfill>()
    run.execute()
    // The filter skips album info rows (sort_key starts with "INFO_"), so nonTrackCount should be 0.
    assertThat(run.backfill.nonTrackCount).isEqualTo(0)
  }

  @Test
  fun `operatorStrategy configures backfill execution`() {
    testData.addThriller()

    val run = backfila.createWetRun<OperatorStrategyBackfill>()
    run.execute()
    assertThat(run.backfill.processedCount).isGreaterThan(0)
  }

  /** Uses [DynamoDbDataDefinition] for filtering instead of method overrides. */
  class DataDefinitionFilterBackfill @Inject constructor(
    dynamoDb: DynamoDbClient,
    private val dynamoDbEnhancedClient: DynamoDbEnhancedClient,
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, NoParameters>(dynamoDb) {
    var nonTrackCount = 0

    override val dataDefinition by lazy {
      DynamoDbDataDefinition(
        dynamoDbTable = dynamoDbEnhancedClient.table(
          TrackItem.TABLE_NAME,
          TableSchema.fromClass(TrackItem::class.java),
        ),
        filterExpression = "begins_with (sort_key, :sort_start)",
        expressionAttributeValues = mapOf(
          ":sort_start" to AttributeValue.builder().s("TRACK_").build(),
        ),
      )
    }

    override fun runOne(item: TrackItem, config: BackfillConfig<NoParameters>): Boolean {
      if (item.sort_key?.startsWith("TRACK_") == true) {
        val trackTitle = item.track_title ?: return false
        if (trackTitle.endsWith(" (EXPLICIT)")) return false
        item.track_title = "$trackTitle (EXPLICIT)"
      } else {
        nonTrackCount++
      }
      return true
    }

    override fun dynamoDbTable(): DynamoDbTable<TrackItem> {
      return dynamoDbEnhancedClient.table(
        TrackItem.TABLE_NAME,
        TableSchema.fromClass(TrackItem::class.java),
      )
    }
  }

  /** Uses [DynamoDbOperatorStrategy] for execution config instead of method overrides. */
  class OperatorStrategyBackfill @Inject constructor(
    dynamoDb: DynamoDbClient,
    private val dynamoDbEnhancedClient: DynamoDbEnhancedClient,
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, NoParameters>(dynamoDb) {
    var processedCount = 0

    override val operatorStrategy = DynamoDbOperatorStrategy(
      partitionCount = 4,
      fixedSegmentCount = 4,
      mustHaveProvisionedBillingMode = true,
    )

    override fun runOne(item: TrackItem, config: BackfillConfig<NoParameters>): Boolean {
      processedCount++
      return false // Don't save, just count
    }

    override fun dynamoDbTable(): DynamoDbTable<TrackItem> {
      return dynamoDbEnhancedClient.table(
        TrackItem.TABLE_NAME,
        TableSchema.fromClass(TrackItem::class.java),
      )
    }
  }
}
