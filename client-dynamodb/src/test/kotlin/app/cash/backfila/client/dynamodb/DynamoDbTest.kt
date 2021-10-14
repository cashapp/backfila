package app.cash.backfila.client.dynamodb

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector

@MiskTest(startService = true)
class DynamoDbBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject lateinit var dynamoDb: DynamoDBMapper
  @Inject lateinit var backfila: Backfila
  @Inject lateinit var testData: DynamoMusicTableTestData
  @Inject lateinit var logCollector: LogCollector

  @Test
  fun `happy path`() {
    val trackItem = TrackItem().apply {
      this.album_token = "ALBUM_2"
      this.sort_key = "TRACK_03"
      this.track_title = "Thriller"
    }
    dynamoDb.save(trackItem)

    val run = backfila.createWetRun<MakeTracksExplicitBackfill>()
    run.execute()

    val updatedTrackItem = dynamoDb.load(trackItem)!!
    assertThat(updatedTrackItem.track_title).isEqualTo("Thriller (EXPLICIT)")
    assertThat(run.backfill.finalized).isTrue()
  }

  @Test
  fun `validating fails creations`() {
    testData.addThriller()

    assertThatCode {
      backfila.createWetRun<MakeTracksExplicitBackfill>(
        parameterData = mapOf("validate" to "false".encodeUtf8())
      )
    }.hasMessageContaining("Validate failed")
  }

  class MakeTracksExplicitBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, MakeTracksExplicitBackfill.ExplicitParameters>(dynamoDb) {

    var finalized = false
      private set

    override fun runOne(item: TrackItem, config: BackfillConfig<ExplicitParameters>): Boolean {
      val trackTitle = item.track_title ?: return false
      if (trackTitle.endsWith(" (EXPLICIT)")) return false // Idempotent retry?
      item.track_title = "$trackTitle (EXPLICIT)"
      return true
    }

    override fun validate(config: BackfillConfig<ExplicitParameters>) {
      check(config.parameters.validate) { "Validate failed" }
    }

    data class ExplicitParameters(
      val validate: Boolean = true
    )

    override fun fixedSegmentCount(config: BackfillConfig<ExplicitParameters>): Int? = 16

    override fun finalize() {
      require(!finalized) { "multiple finalize calls" }
      finalized = true
    }
  }
}
