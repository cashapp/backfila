package app.cash.backfila.client.dynamodbv2

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import java.util.stream.Collectors
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

@MiskTest(startService = true)
class DynamoDbIndexTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject
  lateinit var dynamoDb: DynamoDbTable<TrackItem>

  @Inject
  lateinit var backfila: Backfila

  @Inject
  lateinit var testData: TrackData

  @Test
  fun `only items in the specified index are processed`() {
    val trackItem = TrackItem().apply {
      this.album_token = "ALBUM_2"
      this.sort_key = "TRACK_03"
      this.track_title = "Thriller"
      this.album_title = "MJ"
    }
    dynamoDb.putItem(trackItem)

    val trackItem2 = TrackItem().apply {
      this.album_token = "ALBUM_3"
      this.sort_key = "TRACK_01"
      this.track_title = "Anon"
      // No album title so its excluded from the trackTitleIndex sparse GSI.
    }
    dynamoDb.putItem(trackItem2)

    assertThat(rowsInIndex().size).isEqualTo(1)
    assertThat(testData.getRowsDump().size).isEqualTo(2)

    val run = backfila.createWetRun<MakeTracksAsSinglesBackfill>()
    run.execute()

    // Only rows from the index were updated.
    assertThat(dynamoDb.getItem(Key.builder().partitionValue("ALBUM_2").sortValue("TRACK_03").build()).track_title)
      .isEqualTo("Thriller (Single)")
    assertThat(dynamoDb.getItem(Key.builder().partitionValue("ALBUM_3").sortValue("TRACK_01").build()).track_title)
      .isEqualTo("Anon")
  }

  private fun rowsInIndex(): List<TrackItem> {
    val scanRequest = ScanEnhancedRequest.builder()
      .limit(10000)
      .build()
    return dynamoDb.index("trackTitleIndex").scan(scanRequest).stream().flatMap { it.items().stream() }.collect(Collectors.toList())
  }

  class MakeTracksAsSinglesBackfill @Inject constructor(
    dynamoDb: DynamoDbClient,
    private val dynamoDbEnhancedClient: DynamoDbEnhancedClient,
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, MakeTracksAsSinglesBackfill.SingleParameters>(dynamoDb) {

    override fun runOne(item: TrackItem, config: BackfillConfig<SingleParameters>): Boolean {
      val trackTitle = item.track_title ?: return false
      if (trackTitle.endsWith(" (Single)")) return true
      item.track_title = "$trackTitle (Single)"
      return true
    }

    data class SingleParameters(val validate: Boolean = true)

    override fun indexName(config: BackfillConfig<SingleParameters>): String? = "trackTitleIndex"
    override fun dynamoDbTable(): DynamoDbTable<TrackItem> {
      return dynamoDbEnhancedClient.table(
        TrackItem.TABLE_NAME,
        TableSchema.fromClass(TrackItem::class.java),
      )
    }
  }
}
