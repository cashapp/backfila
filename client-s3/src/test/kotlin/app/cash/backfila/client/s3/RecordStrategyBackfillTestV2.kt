package app.cash.backfila.client.s3

import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.s3.record.RecordStrategy
import app.cash.backfila.client.s3.shim.FakeS3Service
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.BufferedSource
import okio.ByteString
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class RecordStrategyBackfillTestV2 {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModuleV2()

  @Inject lateinit var backfila: Backfila

  @Inject lateinit var fakeS3: FakeS3Service

  @BeforeEach
  fun `loadFiles`() {
    fakeS3.loadResourceDirectory("file-structure")
  }

  @Test
  fun `backfilling with a record strategy that consumes no bytes fails`() {
    assertThatThrownBy {
      backfila.createWetRun<BrokenBreakfastBackfill>().execute()
    }.hasMessageContaining("Failed to consume any streamed bytes")
  }

  class BrokenBreakfastBackfill @Inject constructor() : S3DatasourceBackfill<String, NoParameters>() {
    override fun getBucket(config: PrepareBackfillConfig<NoParameters>) =
      "in-the-kitchen-with-mikepaw"

    override fun getPrefix(config: PrepareBackfillConfig<NoParameters>) = "$staticPrefix/"

    override val staticPrefix = "breakfast"

    override val recordStrategy: RecordStrategy<String> = object : RecordStrategy<String> {
      override fun calculateNextRecordBytes(source: BufferedSource) = 0L

      override fun bytesToRecords(source: ByteString): List<String> {
        TODO("Not yet implemented")
      }
    }
  }
}
