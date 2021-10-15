package app.cash.backfila.client.dynamodbv2

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import javax.inject.Inject

@MiskTest(startService = true)
class DynamoDbFilteringTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject
  lateinit var backfila: Backfila

  @Inject
  lateinit var testData: TrackData

  @Test
  fun `lazy filter processes all the noop album info rows`() {
    testData.addAllTheMusic()

    // Lazy Filter at run time touches all the album info entries too.
    val run = backfila.createWetRun<FilteredMakeTracksExplicitBackfill>()
    run.execute()
    assertThat(run.backfill.nonTrackCount).isEqualTo(5)
  }

  @Test
  fun `dynamo filter doesn't process the album info rows`() {
    testData.addAllTheMusic()

    // Dynamo Filter at run time skips all the album info entries too.
    val run = backfila.createWetRun<DynamoFilterMakeTracksExplicitBackfill>()
    run.execute()
    assertThat(run.backfill.nonTrackCount).isEqualTo(0)
  }

  open class FilteredMakeTracksExplicitBackfill @Inject constructor(
    dynamoDb: DynamoDbClient,
    private val dynamoDbEnhancedClient: DynamoDbEnhancedClient,
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, NoParameters>(dynamoDb) {
    var nonTrackCount: Int = 0

    override fun runOne(item: TrackItem, config: BackfillConfig<NoParameters>): Boolean {
      if (item.sort_key?.startsWith(TRACK_SORT_START) == true) {
        val trackTitle = item.track_title ?: return false
        if (trackTitle.endsWith(" (EXPLICIT)")) return false // Idempotent retry?
        item.track_title = "$trackTitle (EXPLICIT)"
      } else {
        nonTrackCount++
      }
      return true
    }

    companion object {
      const val TRACK_SORT_START = "TRACK_"
    }

    override fun fixedSegmentCount(config: BackfillConfig<NoParameters>): Int? = 16
    override fun dynamoDbTable(): DynamoDbTable<TrackItem> {
      return dynamoDbEnhancedClient.table(
        TrackItem.TABLE_NAME,
        TableSchema.fromClass(TrackItem::class.java)
      )
    }
  }

  class DynamoFilterMakeTracksExplicitBackfill @Inject constructor(
    dynamoDb: DynamoDbClient,
    private val dynamoDbEnhancedClient: DynamoDbEnhancedClient,
  ) : FilteredMakeTracksExplicitBackfill(dynamoDb, dynamoDbEnhancedClient) {
    override fun dynamoDbTable(): DynamoDbTable<TrackItem> {
      return dynamoDbEnhancedClient.table(
        TrackItem.TABLE_NAME,
        TableSchema.fromClass(TrackItem::class.java)
      )
    }

    override fun filterExpression(config: BackfillConfig<NoParameters>): String? =
      "begins_with (sort_key, :sort_start)"

    override fun expressionAttributeValues(config: BackfillConfig<NoParameters>): Map<String, AttributeValue>? =
      mapOf(":sort_start" to AttributeValue.builder().s(TRACK_SORT_START).build())
  }
}
