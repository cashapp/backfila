package app.cash.backfila.client.dynamodbv2

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.misk.TestingModule
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient

@MiskTest(startService = true)
class DynamoDbLastEvaluatedKeyTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject
  lateinit var backfila: Backfila

  @Inject
  lateinit var testData: TrackData

  @Test
  fun `one big segment completes across scan pages`() {
    testData.addThriller()

    val run = backfila.createWetRun<PaginatingBackfill>(
      parameters = PaginatingBackfill.Parameters(
        segmentCount = 1, partitionCount = 1,
        requireMaxBatchSize = 5,
      ),
    )
    run.batchSize = 5
    run.execute()

    val rows = testData.getTracksDump()
    assertThat(rows).extracting<String> { it.track_title }.allMatch { it.endsWith("(EXPLICIT)") }
  }

  @Test
  fun `multiple partitions complete across scan pages`() {
    testData.addLinkinPark()

    val run = backfila.createWetRun<PaginatingBackfill>(
      parameters = PaginatingBackfill.Parameters(
        segmentCount = 4, partitionCount = 2,
        requireMaxBatchSize = 5,
      ),
    )
    run.batchSize = 5
    run.execute()

    val rows = testData.getTracksDump()
    assertThat(rows).extracting<String> { it.track_title }.allMatch { it.endsWith("(EXPLICIT)") }
  }

  class PaginatingBackfill @Inject constructor(
    dynamoDb: DynamoDbClient,
    private val dynamoDbEnhancedClient: DynamoDbEnhancedClient,
  ) : UpdateInPlaceDynamoDbBackfill<TrackItem, PaginatingBackfill.Parameters>(dynamoDb) {

    override fun dynamoDbTable(): DynamoDbTable<TrackItem> {
      return dynamoDbEnhancedClient.table(
        TrackItem.TABLE_NAME,
        TableSchema.fromClass(TrackItem::class.java),
      )
    }

    override fun runBatch(items: List<TrackItem>, config: BackfillConfig<Parameters>) {
      require(items.size <= config.parameters.requireMaxBatchSize)
      super.runBatch(items, config)
    }

    override fun runOne(item: TrackItem, config: BackfillConfig<Parameters>): Boolean {
      val trackTitle = item.track_title ?: return false
      if (trackTitle.endsWith(" (EXPLICIT)")) return false // Idempotent retry?
      item.track_title = "$trackTitle (EXPLICIT)"
      return true
    }

    data class Parameters(
      val segmentCount: Int = 4,
      val partitionCount: Int = 2,
      val requireMaxBatchSize: Long = 100L,
    )

    override fun fixedSegmentCount(config: PrepareBackfillConfig<Parameters>): Int? =
      config.parameters.segmentCount

    override fun partitionCount(config: PrepareBackfillConfig<Parameters>): Int =
      config.parameters.partitionCount
  }
}
