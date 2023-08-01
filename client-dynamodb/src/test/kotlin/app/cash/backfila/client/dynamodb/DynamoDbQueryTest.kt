package app.cash.backfila.client.dynamodb

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DynamoDbQueryTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject lateinit var dynamoDb: DynamoDBMapper

  @Inject lateinit var backfila: Backfila

  @Inject lateinit var testData: DynamoMusicTableTestData

  @Test
  fun `dynamo query process all albums with PK`() {
    testData.addAllTheMusic()

    val run = backfila.createWetRun<MakeTracksRemixBackfill>()
    run.batchSize = 10
    run.execute()

    assertThat(run.backfill.tracksEdited).isEqualTo(9)
  }

  @Test
  fun `dynamo query only updates single track`() {
    val trackItem = TrackItem().apply {
      this.album_token = "ALBUM_2"
      this.sort_key = "TRACK_03"
      this.track_title = "Thriller"
      this.album_title = "MJ"
    }
    dynamoDb.save(trackItem)
    testData.addAllTheMusic()

    val run = backfila.createWetRun<WithGSIMakeTracksRemixBackfill>()
    run.execute()
    assertThat(run.backfill.tracksEdited).isEqualTo(1)
    assertThat(dynamoDb.load(TrackItem::class.java, "ALBUM_2", "TRACK_03").track_title)
      .isEqualTo("Thriller (REMIX)")
  }

  @Test
  fun `dynamoDB query with incorrect config fails`() {
    Assertions.assertThatCode {
      backfila.createWetRun<WithPartitionSegmentBackfill>()
    }.hasMessageContaining("Query cannot utilise segments, please run in single batch")
  }
  open class QueryMakeTracksRemixBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper,
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, NoParameters>(dynamoDb) {
    var tracksEdited: Int = 0
    override fun runOne(item: TrackItem, config: BackfillConfig<NoParameters>): Boolean {
      if (item.sort_key?.startsWith(TRACK_SORT_START) == true) {
        val trackTitle = item.track_title ?: return false
        if (trackTitle.endsWith(" (REMIX)")) return false // Idempotent retry?
        item.track_title = "$trackTitle (REMIX)"
        tracksEdited++
      }
      return true
    }
    companion object {
      const val TRACK_SORT_START = "TRACK_"
    }

    override fun useQueryRequest(): Boolean = true
    override fun fixedSegmentCount(config: PrepareBackfillConfig<NoParameters>): Int? = 1
    override fun partitionCount(config: PrepareBackfillConfig<NoParameters>): Int = 1
  }

  class MakeTracksRemixBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper,
  ) : QueryMakeTracksRemixBackfill(dynamoDb) {
    override fun keyConditionExpression(config: BackfillConfig<NoParameters>): String? = "album_token=:val1"

    override fun expressionAttributeValues(config: BackfillConfig<NoParameters>): Map<String, AttributeValue>? =
      mapOf(":val1" to AttributeValue("ALBUM_2"))
  }

  class WithGSIMakeTracksRemixBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper,
  ) : QueryMakeTracksRemixBackfill(dynamoDb) {

    override fun keyConditionExpression(config: BackfillConfig<NoParameters>): String? = "track_title=:val1"

    override fun expressionAttributeValues(config: BackfillConfig<NoParameters>): Map<String, AttributeValue>? =
      mapOf(":val1" to AttributeValue("Thriller"))

    override fun indexName(config: BackfillConfig<NoParameters>): String? = "trackTitleIndex"

    override fun isConsistentRead(config: BackfillConfig<NoParameters>): Boolean = false
  }
  class WithPartitionSegmentBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper,
  ) : QueryMakeTracksRemixBackfill(dynamoDb) {
    override fun fixedSegmentCount(config: PrepareBackfillConfig<NoParameters>): Int? = 16
  }
}
