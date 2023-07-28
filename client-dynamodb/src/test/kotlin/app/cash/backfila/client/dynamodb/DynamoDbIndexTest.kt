package app.cash.backfila.client.dynamodb

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DynamoDbIndexTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject lateinit var dynamoDb: DynamoDBMapper

  @Inject lateinit var backfila: Backfila

  @Inject lateinit var testData: DynamoMusicTableTestData

  @Test
  fun `only items in the specified index are processed`() {
    val trackItem = TrackItem().apply {
      this.album_token = "ALBUM_2"
      this.sort_key = "TRACK_03"
      this.track_title = "Thriller"
      this.album_title = "MJ"
    }
    dynamoDb.save(trackItem)

    val trackItem2 = TrackItem().apply {
      this.album_token = "ALBUM_3"
      this.sort_key = "TRACK_01"
      this.track_title = "Anon"
      // No album title so its excluded from the trackTitleIndex sparse GSI.
    }
    dynamoDb.save(trackItem2)

    assertThat(rowsInIndex().size).isEqualTo(1)
    assertThat(testData.getRowsDump().size).isEqualTo(2)

    val run = backfila.createWetRun<MakeTracksAsSinglesBackfill>()
    run.execute()

    // Only rows from the index were updated.
    assertThat(dynamoDb.load(TrackItem::class.java, "ALBUM_2", "TRACK_03").track_title)
      .isEqualTo("Thriller (Single)")
    assertThat(dynamoDb.load(TrackItem::class.java, "ALBUM_3", "TRACK_01").track_title)
      .isEqualTo("Anon")
  }

  private fun rowsInIndex(): List<TrackItem> {
    val scanRequest = DynamoDBScanExpression().apply {
      limit = 10000
      indexName = "trackTitleIndex"
    }
    return dynamoDb.scan(TrackItem::class.java, scanRequest)
  }

  class MakeTracksAsSinglesBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper,
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, MakeTracksAsSinglesBackfill.SingleParameters>(dynamoDb) {

    override fun runOne(item: TrackItem, config: BackfillConfig<SingleParameters>): Boolean {
      val trackTitle = item.track_title ?: return false
      if (trackTitle.endsWith(" (Single)")) return true
      item.track_title = "$trackTitle (Single)"
      return true
    }

    data class SingleParameters(val validate: Boolean = true)

    override fun indexName(config: BackfillConfig<SingleParameters>): String? = "trackTitleIndex"
  }
}
