package app.cash.backfila.client.dynamodb

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DynamoDbQueryTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject lateinit var backfila: Backfila

  @Inject lateinit var testData: DynamoMusicTableTestData

  @Test
  fun `dynamo filter doesn't process the album info rows`() {
    testData.addAllTheMusic()

    val run = backfila.createWetRun<DynamoQueryMakeTracksRemixBackfill>()
    run.batchSize = 3
    run.execute()
    val total = run.backfill.total

    assertThat(run.backfill.tracksEdited).isEqualTo(9)
  }

  @Test
  fun `dynamo query only updates single album`() {
    testData.addAllTheMusic()

    // Dynamo Filter using expression names at run time skips all the album info entries too.
    val run = backfila.createWetRun<DynamoFilterWithAttributeNameMakeTracksRemixBackfill>()
    run.execute()
    assertThat(run.backfill.tracksEdited).isEqualTo(9)
  }

  open class QueryMakeTracksRemixBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper,
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, NoParameters>(dynamoDb) {
    var nonTrackCount: Int = 0
    var tracksEdited: Int = 0
    var total: Int = 0
    override fun runOne(item: TrackItem, config: BackfillConfig<NoParameters>): Boolean {
      total++
      println("${item.album_token} + ${item.sort_key}")
      if (item.sort_key?.startsWith(TRACK_SORT_START) == true) {
        val trackTitle = item.track_title ?: return false
        if (trackTitle.endsWith(" (REMIX)")) return false // Idempotent retry?
        item.track_title = "$trackTitle (REMIX)"
        tracksEdited++
      } else {
        nonTrackCount++
      }
      return true
    }
    companion object {
      const val TRACK_SORT_START = "TRACK_"
    }
    override fun useQueryRequest(config: BackfillConfig<NoParameters>): Boolean {
      return true
    }
  }

  class DynamoQueryMakeTracksRemixBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper,
  ) : QueryMakeTracksRemixBackfill(dynamoDb) {
    override fun keyConditionExpression(config: BackfillConfig<NoParameters>): String? {
      return "album_token=:val1"
    }
    override fun expressionAttributeValues(config: BackfillConfig<NoParameters>): Map<String, AttributeValue>? =
      mapOf(":val1" to AttributeValue("ALBUM_2"))
  }

  class DynamoFilterWithAttributeNameMakeTracksRemixBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper,
  ) : QueryMakeTracksRemixBackfill(dynamoDb) {

    override fun keyConditionExpression(config: BackfillConfig<NoParameters>): String? {
      return "artist_name=:val1"
    }

    override fun expressionAttributeValues(config: BackfillConfig<NoParameters>): Map<String, AttributeValue>? =
      mapOf(":val1" to AttributeValue("Michael Jackson"))

    override fun indexName(config: BackfillConfig<NoParameters>): String? {
      return "artistNameIndex"
    }
  }
}
