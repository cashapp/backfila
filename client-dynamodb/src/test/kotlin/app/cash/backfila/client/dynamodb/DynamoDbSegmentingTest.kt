package app.cash.backfila.client.dynamodb

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.client.dynamodb.DynamoDbSegmentingTest.SegmentingBackfill.SegmentingParameters
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@MiskTest(startService = true)
class DynamoDbSegmentingTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject lateinit var backfila: Backfila
  @Inject lateinit var testData: DynamoMusicTableTestData

  @Test
  fun `segments must be ge partitions`() {
    testData.addThriller()

    assertThrows<IllegalArgumentException> {
      backfila.createWetRun<SegmentingBackfill>(
        parameters = SegmentingParameters(2, 4)
      )
    }
  }

  @Test
  fun `segment and partition count cannot be zero or negative`() {
    testData.addThriller()

    assertThrows<IllegalArgumentException> {
      backfila.createWetRun<SegmentingBackfill>(
        parameters = SegmentingParameters(0, 4)
      )
    }

    assertThrows<IllegalArgumentException> {
      backfila.createWetRun<SegmentingBackfill>(
        parameters = SegmentingParameters(2, 0)
      )
    }

    assertThrows<IllegalArgumentException> {
      backfila.createWetRun<SegmentingBackfill>(
        parameters = SegmentingParameters(2, -1)
      )
    }

    assertThrows<IllegalArgumentException> {
      backfila.createWetRun<SegmentingBackfill>(
        parameters = SegmentingParameters(0, 0)
      )
    }
  }

  @Test
  fun `segments can = partitions`() {
    testData.addThriller()

    val run = backfila.createWetRun<SegmentingBackfill>(
      parameters = SegmentingParameters(2, 2)
    )
    run.execute()
    assertThat(run.backfill.updateCounter).isEqualTo(9)
  }

  @Test
  fun `happy case`() {
    testData.addLinkinPark()

    val run = backfila.createWetRun<SegmentingBackfill>(
      parameters = SegmentingParameters(8, 2)
    )

    assertThat(run.prepareBackfillResponse.partitions).hasSize(2)
      .extracting<Int> { it.backfill_range.start.utf8().split("/").first().last().toString().toInt() }
      .contains(0, 4)

    run.execute()
    assertThat(run.backfill.updateCounter).isEqualTo(25)
  }

  class SegmentingBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, SegmentingParameters>(dynamoDb) {
    var updateCounter: Int = 0

    override fun runOne(item: TrackItem, config: BackfillConfig<SegmentingParameters>): Boolean {
      val trackTitle = item.track_title ?: return false
      if (trackTitle.endsWith(" (EXPLICIT)")) return false // Idempotent retry?
      item.track_title = "$trackTitle (EXPLICIT)"
      updateCounter++
      return true
    }

    data class SegmentingParameters(
      val segmentCount: Int = 4,
      val partitionCount: Int = 2
    )

    override fun fixedSegmentCount(config: BackfillConfig<SegmentingParameters>): Int? = config.parameters.segmentCount

    override fun partitionCount(config: BackfillConfig<SegmentingParameters>): Int = config.parameters.partitionCount
  }
}
