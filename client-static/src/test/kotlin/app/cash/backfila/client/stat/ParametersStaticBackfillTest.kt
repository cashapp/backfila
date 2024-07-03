package app.cash.backfila.client.stat

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.stat.parameters.CsvDatasourceParameters
import app.cash.backfila.client.stat.parameters.DatasourceParameters
import app.cash.backfila.client.stat.parameters.ParametersDatasourceBackfill
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createDryRun
import app.cash.backfila.embedded.createWetRun
import com.squareup.wire.internal.newMutableList
import javax.inject.Inject
import kotlin.test.assertFails
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class ParametersStaticBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject lateinit var backfila: Backfila

  @Test
  fun `backfilling artisan cheese`() {
    val run = backfila.createWetRun<ArtisanCheeseBackfill>(
      parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
    )
    run.execute()

    assertThat(run.backfill.backfilledCheese).hasSize(11)
    assertThat(run.backfill.backfilledCheese).contains("Brie", "Blue", "Goat", "Swiss", "Havarti", "Manchego")
  }

  @Test
  fun `backfilling processed cheese`() {
    val run = backfila.createWetRun<ProcessedCheeseBackfill>(
      parameterData = mapOf("cheeseCSV" to allProcessedCheese.encodeUtf8()),
    )
    run.execute()

    assertThat(run.backfill.backfilledCheese).hasSize(6)
    assertThat(run.backfill.backfilledCheese).contains(ProcessedCheese.LAUGHING_COW, ProcessedCheese.VELVEETA)
  }

  @Test
  fun `backfilling lowercase processed cheese fails`() {
    val exception = assertFails {
      backfila.createWetRun<ProcessedCheeseBackfill>(
        parameterData = mapOf("cheeseCSV" to allProcessedCheese.lowercase().encodeUtf8()),
      )
    }
    assertThat(exception).hasMessageContaining("No enum constant")
  }

  @Test
  fun `dry run doesn't backfill`() {
    val run = backfila.createDryRun<ArtisanCheeseBackfill>(
      parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
    )
    run.execute()

    assertThat(run.backfill.backfilledCheese).hasSize(0)
  }

  @Test
  fun `test single batch`() {
    val run = backfila.createWetRun<ArtisanCheeseBackfill>(
      parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
    )
    run.batchSize = 5
    run.scanRemaining()
    run.runBatch()
    assertThat(run.backfill.backfilledCheese).hasSize(5)
  }

  @Test
  fun `test rangeStart and rangeEnd`() {
    val run = backfila.createWetRun<ArtisanCheeseBackfill>(
      rangeStart = "2",
      rangeEnd = "8",
      parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
    )
    run.batchSize = 3
    run.execute()
    assertThat(run.backfill.backfilledCheese).hasSize(6)
  }

  @Test
  fun `test rangeStart`() {
    val run = backfila.createWetRun<ArtisanCheeseBackfill>(
      rangeStart = "3",
      parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
    )
    run.batchSize = 3
    run.execute()
    assertThat(run.backfill.backfilledCheese).hasSize(8)
  }

  @Test
  fun `test rangeEnd`() {
    val run = backfila.createWetRun<ArtisanCheeseBackfill>(
      rangeEnd = "8",
      parameterData = mapOf(
        "csvData" to artisanCheeses.encodeUtf8(),
      ),
    )
    run.batchSize = 3
    run.execute()
    assertThat(run.backfill.backfilledCheese).hasSize(8)
  }

  @Test
  fun `validation failures`() {
    with(SoftAssertions()) {
      this.assertThatCode {
        backfila.createWetRun<ArtisanCheeseBackfill>(
          rangeStart = "abc",
          parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
        )
      }.hasMessageContaining("must be a number")

      this.assertThatCode {
        backfila.createWetRun<ArtisanCheeseBackfill>(
          rangeEnd = "abc",
          parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
        )
      }.hasMessageContaining("must be a number")

      this.assertThatCode {
        backfila.createWetRun<ArtisanCheeseBackfill>(
          rangeStart = "-10",
          parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
        )
      }.hasMessageContaining("must be positive integers")

      this.assertThatCode {
        backfila.createWetRun<ArtisanCheeseBackfill>(
          rangeEnd = "-5",
          parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
        )
      }.hasMessageContaining("must be positive integers")

      this.assertThatCode {
        backfila.createWetRun<ArtisanCheeseBackfill>(
          rangeStart = "5",
          rangeEnd = "2",
          parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
        )
      }.hasMessageContaining("Start must be less than or equal to end")

      this.assertThatCode {
        backfila.createWetRun<ArtisanCheeseBackfill>(
          rangeStart = "20",
          rangeEnd = "30",
          parameterData = mapOf("csvData" to artisanCheeses.encodeUtf8()),
        )
      }.hasMessageContaining("Start is greater than the static datasource size")

      this.assertAll()
    }
  }

  class ArtisanCheeseBackfill @Inject constructor() : ParametersDatasourceBackfill<String, CsvDatasourceParameters>() {
    val backfilledCheese = newMutableList<String>()

    override fun runOne(item: String, config: BackfillConfig<CsvDatasourceParameters>) {
      if (!config.dryRun) {
        backfilledCheese.add(item)
      }
    }
  }

  class ProcessedCheeseBackfill @Inject constructor() : ParametersDatasourceBackfill<ProcessedCheese, ProcessedCheeseBackfill.ProcessedCheeseParameters>() {
    val backfilledCheese = newMutableList<ProcessedCheese>()

    override fun runOne(item: ProcessedCheese, config: BackfillConfig<ProcessedCheeseParameters>) {
      if (!config.dryRun) {
        backfilledCheese.add(item)
      }
    }

    data class ProcessedCheeseParameters(
      val cheeseCSV: String,
    ) : DatasourceParameters<ProcessedCheese> {
      override fun getBackfillData(): List<ProcessedCheese> {
        return cheeseCSV.split(',').map { ProcessedCheese.valueOf(it) }
      }
    }
  }

  companion object {
    enum class ProcessedCheese {
      YELLOW_CHEESE,
      WHITE_CHEESE,
      CHEESE_WHIZ,
      VELVEETA,
      PHILADELPHIA,
      LAUGHING_COW,
    }
    val allProcessedCheese = """
            YELLOW_CHEESE,WHITE_CHEESE,CHEESE_WHIZ,VELVEETA,PHILADELPHIA,LAUGHING_COW
    """.trimIndent()

    val artisanCheeses = """
      Brie,Blue,Goat,Swiss,Havarti,Manchego,OKA,Cambozola,St. Andre,Monteray Jack,Smoked Gouda
    """.trimIndent()
  }
}
