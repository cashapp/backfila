package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.sqldelight.PlayerOriginBackfill.PlayerOriginParameters
import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.persistence.TestHockeyData
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class PlayerOriginBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject lateinit var backfila: Backfila

  @Inject lateinit var hockeyDataDatabase: HockeyDataDatabase

  @Inject lateinit var testData: TestHockeyData

  @BeforeEach
  fun insertTestData() {
    testData.insertAnaheimDucks()
  }

  @Test
  fun `backfilling default players`() {
    hockeyDataDatabase.transaction { }

    val run = backfila.createWetRun<PlayerOriginBackfill>(parameters = PlayerOriginParameters())
    run.execute()

    Assertions.assertThat(run.backfill.backfilledPlayers.size).isEqualTo(7)
  }

  @Test
  fun `validation failures`() {
    with(SoftAssertions()) {
      this.assertThatCode {
        backfila.createWetRun<PlayerOriginBackfill>(
          parameterData = mapOf("validate" to "false".encodeUtf8()),
        )
        fail("validate must fail")
      }.hasMessageContaining("Validate failed")

      // FILL THESE IN LATER!!!!

      this.assertAll()
    }
  }
}
