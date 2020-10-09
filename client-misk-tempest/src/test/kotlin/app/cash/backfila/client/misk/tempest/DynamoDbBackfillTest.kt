package app.cash.backfila.client.misk.tempest

import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.NoParameters
import app.cash.backfila.client.misk.internal.DynamoBackfill
import app.cash.backfila.client.misk.internal.DynamoDbBackfillOperator
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.RunBatchRequest
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class DynamoDbBackfillTest {
  @MiskTestModule
  val module = MusicDbTestModule()

  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var musicDb: MusicDb
  private val musicTable get() = musicDb.music
  @Inject lateinit var musicTestData: MusicTableTestData

  @Inject lateinit var dynamoClient: DynamoDBMapper

  @Test
  fun happyPath() {
    musicTestData.addAllTheMusic()

    val operator = DynamoDbBackfillOperator(
        dynamoDb = dynamoClient,
        backfill = MakeTracksExplicitBackfill,
        config = BackfillConfig(NoParameters(), dryRun = false)
    )
    operator.backfillAll()

    val thriller = musicTable.albumTracks.load(AlbumTrack.Key("ALBUM_2", 3))
    assertThat(thriller!!.track_title).isEqualTo("Thriller (EXPLICIT)")
  }

  private fun DynamoDbBackfillOperator<*, *>.backfillAll() {
    val backfillId = "1"
    val backfillName = name()

    val prepareBackfill = prepareBackfill(PrepareBackfillRequest.Builder()
        .dry_run(false)
        .backfill_id(backfillId)
        .backfill_name(backfillName)
        .build())

    for (partition in prepareBackfill.partitions) {
      var previousEndKey: ByteString? = null
      do {
        val batchRangeResponse = getNextBatchRange(GetNextBatchRangeRequest.Builder()
            .dry_run(false)
            .backfill_id(backfillId)
            .backfill_name(backfillName)
            .previous_end_key(previousEndKey)
            .backfill_range(partition.backfill_range)
            .build())
        previousEndKey = batchRangeResponse.batches.lastOrNull()?.batch_range?.end
        for (batch in batchRangeResponse.batches) {
          runBatch(RunBatchRequest.Builder()
              .dry_run(false)
              .backfill_id(backfillId)

              .backfill_name(backfillName)
              .batch_range(batch.batch_range)
              .build())
        }
      } while (previousEndKey != null)
    }
  }

  object MakeTracksExplicitBackfill : DynamoBackfill<MusicItem, NoParameters> {
    override val itemType: Class<MusicItem> = MusicItem::class.java

    override fun runOne(item: MusicItem, config: BackfillConfig<NoParameters>): MusicItem? {
      if (item.track_title == null) return null
      item.track_title = item.track_title + " (EXPLICIT)"
      return item
    }
  }
}