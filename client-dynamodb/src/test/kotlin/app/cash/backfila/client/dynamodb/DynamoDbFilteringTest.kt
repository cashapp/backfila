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
class DynamoDbFilteringTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject lateinit var backfila: Backfila
  @Inject lateinit var testData: DynamoMusicTableTestData

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
    dynamoDb: DynamoDBMapper
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
  }

  class DynamoFilterMakeTracksExplicitBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper
  ) : FilteredMakeTracksExplicitBackfill(dynamoDb) {

    override fun filterExpression(config: BackfillConfig<NoParameters>): String? =
      "begins_with (sort_key, :sort_start)"

    override fun expressionAttributeValues(config: BackfillConfig<NoParameters>): Map<String, AttributeValue>? =
      mapOf(":sort_start" to AttributeValue(TRACK_SORT_START))
  }
}
