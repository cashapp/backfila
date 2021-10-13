package app.cash.backfila.client.static

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createDryRun
import app.cash.backfila.embedded.createWetRun
import com.squareup.wire.internal.newMutableList
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class StaticKotlinValBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject lateinit var backfila: Backfila

  @Test
  fun `backfilling sauces`() {
    val run = backfila.createWetRun<SaucesBackfill>()
    run.execute()

    assertThat(run.backfill.backfilledSauces.size).isEqualTo(sweetSauces.size + savourySauces.size)
    assertThat(run.backfill.backfilledSauces).containsAll(savourySauces)
    assertThat(run.backfill.backfilledSauces).containsAll(sweetSauces)
  }

  @Test
  fun `marking sauces sweet`() {
    val run = backfila.createWetRun<SaucesBackfill>(
      parameterData = mapOf("markSweet" to "true".encodeUtf8())
    )
    run.execute()

    assertThat(run.backfill.backfilledSauces.size).isEqualTo(sweetSauces.size + savourySauces.size)
    assertThat(run.backfill.backfilledSauces).containsAll(savourySauces)
    assertThat(run.backfill.backfilledSauces).containsAll(sweetSauces.map { it.markSweet() })
  }

  @Test
  fun `dry run doesn't backfill`() {
    val run = backfila.createDryRun<SaucesBackfill>(
      parameterData = mapOf("markSweet" to "true".encodeUtf8())
    )
    run.execute()

    assertThat(run.backfill.backfilledSauces.size).isEqualTo(0)
  }

  @Test
  fun `test single batch`() {
    val run = backfila.createWetRun<SaucesBackfill>()
    run.batchSize = 5
    run.scanRemaining()
    run.runBatch()
    assertThat(run.backfill.backfilledSauces.size).isEqualTo(5)
  }

  @Test
  fun `test rangeStart and rangeEnd`() {
    val run = backfila.createWetRun<SaucesBackfill>(rangeStart = "2", rangeEnd = "8")
    run.batchSize = 3
    run.execute()
    assertThat(run.backfill.backfilledSauces.size).isEqualTo(6)
  }

  @Test
  fun `test rangeStart`() {
    val run = backfila.createWetRun<SaucesBackfill>(rangeStart = "3")
    run.batchSize = 3
    run.execute()
    assertThat(run.backfill.backfilledSauces.size).isEqualTo(7)
  }

  @Test
  fun `test rangeEnd`() {
    val run = backfila.createWetRun<SaucesBackfill>(rangeEnd = "8")
    run.batchSize = 3
    run.execute()
    assertThat(run.backfill.backfilledSauces.size).isEqualTo(8)
  }

  @Test
  fun `validation failures`() {
    with(SoftAssertions()) {
      this.assertThatCode {
        backfila.createWetRun<SaucesBackfill>(
          parameterData = mapOf("validate" to "false".encodeUtf8())
        )
      }.hasMessageContaining("Validate failed")

      this.assertThatCode {
        backfila.createWetRun<SaucesBackfill>(rangeStart = "abc")
      }.hasMessageContaining("must be a number")

      this.assertThatCode {
        backfila.createWetRun<SaucesBackfill>(rangeEnd = "abc")
      }.hasMessageContaining("must be a number")

      this.assertThatCode {
        backfila.createWetRun<SaucesBackfill>(rangeStart = "-10")
      }.hasMessageContaining("must be positive integers")

      this.assertThatCode {
        backfila.createWetRun<SaucesBackfill>(rangeEnd = "-5")
      }.hasMessageContaining("must be positive integers")

      this.assertThatCode {
        backfila.createWetRun<SaucesBackfill>(rangeStart = "5", rangeEnd = "2")
      }.hasMessageContaining("Start must be less than or equal to end")

      this.assertThatCode {
        backfila.createWetRun<SaucesBackfill>(rangeStart = "20", rangeEnd = "30")
      }.hasMessageContaining("Start is greater than the static datasource size")

      this.assertAll()
    }
  }

  class SaucesBackfill @Inject constructor() : StaticDatasourceBackfill<String, SaucesBackfill.SauceAttributes>() {
    val backfilledSauces = newMutableList<String>()

    override fun runOne(item: String, config: BackfillConfig<SauceAttributes>) {
      val sauce = if (config.parameters.markSweet && item in sweetSauces) item.markSweet() else item
      if (!config.dryRun) {
        backfilledSauces.add(sauce)
      }
    }

    override fun validate(config: BackfillConfig<SauceAttributes>) {
      check(config.parameters.validate) { "Validate failed" }
    }

    data class SauceAttributes(
      val markSweet: Boolean = false,
      val validate: Boolean = true
    )

    override val staticDatasource: List<String> = (sweetSauces + savourySauces).shuffled()
  }

  companion object {
    val sweetSauces = listOf("Back-fil-a", "Polynesian", "Honey Mustard", "Barbeque", "Sriracha", "Honey Roasted BBQ")
    val savourySauces = listOf("Garden Herb Ranch", "Zesty Buffalo", "Soy Sauce", "Tzatziki")

    fun String.markSweet() = "SWEET $this SWEET"
  }
}
