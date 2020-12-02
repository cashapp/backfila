package app.cash.backfila.client.misk.dynamodb

import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.client.misk.embedded.Backfila
import app.cash.backfila.client.misk.embedded.createWetRun
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import javax.inject.Inject
import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DynamoDbLastEvaluatedKeyTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Suppress("unused")
  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var backfila: Backfila
  @Inject lateinit var testData: DynamoMusicTableTestData

  @Test
  fun `one big segment with one second batch execution time works`() {
    testData.addThriller()

    // TODO(mikepaw) use batch size to change how many items show up in a batch.
    val run = backfila.createWetRun<PausingBackfill>(
        parameters = PausingBackfill.Parameters(1, 1, 128L)
    )
    run.execute()

    val rows = testData.getTracksDump()
    assertThat(rows).extracting<String> { it.track_title }.allMatch { it.endsWith("(EXPLICIT)") }
  }

  @Test
  fun `really long run batches work`() {
    testData.addLinkinPark()

    // TODO(mikepaw) use batch size to change how many items show up in a batch.
    val run = backfila.createWetRun<PausingBackfill>(
        parameters = PausingBackfill.Parameters(4, 2, 256L)
    )
    run.execute()

    val rows = testData.getTracksDump()
    assertThat(rows).extracting<String> { it.track_title }.allMatch { it.endsWith("(EXPLICIT)") }
  }

  class PausingBackfill @Inject constructor(
    dynamoDb: DynamoDBMapper
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, PausingBackfill.Parameters>(dynamoDb) {

    override fun runOne(item: TrackItem, config: BackfillConfig<Parameters>): Boolean {
      val trackTitle = item.track_title ?: return false
      if (trackTitle.endsWith(" (EXPLICIT)")) return false // Idempotent retry?
      item.track_title = "$trackTitle (EXPLICIT)"
      Thread.sleep(config.parameters.pauseMilliseconds)
      return true
    }

    data class Parameters(
      val segmentCount: Int = 4,
      val partitionCount: Int = 2,
      val pauseMilliseconds: Long = 1000L
    )

    override fun fixedSegmentCount(config: BackfillConfig<Parameters>): Int? = config.parameters.segmentCount

    override fun partitionCount(config: BackfillConfig<Parameters>): Int = config.parameters.partitionCount
  }
}
