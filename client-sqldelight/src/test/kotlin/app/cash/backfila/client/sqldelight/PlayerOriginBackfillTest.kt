package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.sqldelight.PlayerOriginBackfill.PlayerOriginParameters
import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.persistence.TestHockeyData
import app.cash.backfila.client.testing.assertThat
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.BackfillRun
import app.cash.backfila.embedded.createWetRun
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
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
    val run = backfila.createWetRun<PlayerOriginBackfill>(parameters = PlayerOriginParameters())
    run.execute()

    // There are 7 canadian players.
    assertThat(run.backfill.backfilledPlayers).hasSize(7)
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

  @Test fun emptyTable() {
    // Someone fired the whole team.
    hockeyDataDatabase.transaction {
      hockeyDataDatabase.hockeyPlayerQueries.selectAll().executeAsList().forEach {
        hockeyDataDatabase.hockeyPlayerQueries.deletePlayer(it.player_number)
      }
    }
    val run = backfila.createWetRun<PlayerOriginBackfill>()
    assertThat(run.partitionProgressSnapshot.values.single().utf8RangeStart()).isNull()
    assertThat(run.partitionProgressSnapshot.values.single().utf8RangeEnd()).isNull()
    // Trying to scan for a batch on an empty tablet gets nothing to execute.
    assertThat(run.singleScan().batches).isEmpty() // Check the returned scan
    assertThat(run).isFinishedScanning()
      .hasNoBatchesToRun()
      .isComplete()
    // Parameters are only recorded if work is done
    assertThat(run.backfill.backfilledPlayers).isEmpty()
  }

  @Test fun noMatches() {
    // Someone fired all the Canadians.
    hockeyDataDatabase.transaction {
      hockeyDataDatabase.hockeyPlayerQueries.selectByPlaceOfBirthLike("%CAN").executeAsList().forEach {
        hockeyDataDatabase.hockeyPlayerQueries.deletePlayer(it.player_number)
      }
    }
    val run = backfila.createWetRun<PlayerOriginBackfill>()
      .apply { configureForTest() }
    assertThat(run.partitionProgressSnapshot.values.single().utf8RangeStart()).isNotNull()
    assertThat(run.partitionProgressSnapshot.values.single().utf8RangeEnd()).isNotNull()
    assertThat(run.partitionProgressSnapshot.values.single().previousEndKey).isNull()

    // We still find all the records but the backfill itself filters them out.
    val scan1 = run.singleScan()
    assertThat(scan1.batches).hasSize(1)
    assertThat(scan1.batches.single().scanned_record_count).isEqualTo(9)
    assertThat(scan1.batches.single().matching_record_count).isEqualTo(9)
    assertThat(run.partitionProgressSnapshot.values.single()).isNotDone()
    assertThat(run.partitionProgressSnapshot.values.single().previousEndKey).isNotNull

    val scan2 = run.singleScan()
    assertThat(scan2.batches).isEmpty()
    assertThat(run.partitionProgressSnapshot.values.single()).isDone()
    run.execute()

    assertThat(run.backfill.backfilledPlayers).isEmpty()
  }

  @Test fun withStartRange() {
    // Skip the single digit players.
    val run = backfila.createWetRun<PlayerOriginBackfill>(rangeStart = "10")
      .apply { configureForTest() }
    assertThat(run.rangeStart).isEqualTo("10")
    assertThat(run.partitionProgressSnapshot.values.single()).isNotDone()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo("11")
    assertThat(run.partitionProgressSnapshot.values.single().utf8PreviousEndKey()).isEqualTo("38")

    run.execute()
    assertThat(run.backfill.backfilledPlayers).hasSize(7)
  }

  @Test fun withEndRange() {
    // Only backfill the single digit players.
    val run = backfila.createWetRun<PlayerOriginBackfill>(
      rangeEnd = "10",
      parameterData = mapOf("originRegex" to "USA".encodeUtf8()),
    ).apply { configureForTest() }
    assertThat(run.rangeEnd).isEqualTo("10")
    assertThat(run.partitionProgressSnapshot.values.single()).isNotDone()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo("7")
    assertThat(run.batchesToRunSnapshot.single().matchingRecordCount).isEqualTo(1)
    assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(1)
    run.runBatch()

    run.singleScan()
    assertThat(run).hasNoBatchesToRun()
    assertThat(run).isFinishedScanning()

    assertThat(run).isComplete()
    assertThat(run.backfill.backfilledPlayers).singleElement().isNotNull
  }

  @Test fun multipleBatches() {
    val run1 = backfila.createWetRun<PlayerOriginBackfill>()
      .apply { configureForTest() }

    run1.computeCountLimit = 2L
    val scan = run1.singleScan()
    assertThat(scan.batches).hasSize(2)
    assertThat(scan.batches[0].batch_range.end).isLessThan(scan.batches[1].batch_range.start)

    // Requesting two batches should give the same batches as requesting one twice.
    val run2 = backfila.createWetRun<PlayerOriginBackfill>()
      .apply { configureForTest() }
    val scan1 = run2.singleScan()
    val scan2 = run2.singleScan()

    assertThat(scan.batches[0]).isEqualTo(scan1.batches.single())
    assertThat(scan.batches[1]).isEqualTo(scan2.batches.single())
  }

  @Test fun multipleScans() {
    val run1 = backfila.createWetRun<PlayerOriginBackfill>()
    run1.batchSize = 2L
    run1.scanSize = 4L
    run1.computeCountLimit = 3
    val scan = run1.singleScan()
    assertThat(scan.batches).hasSize(3)
    assertThat(scan.batches[0].batch_range.end).isLessThan(scan.batches[1].batch_range.start)
    assertThat(scan.batches[1].batch_range.end).isLessThan(scan.batches[2].batch_range.start)

    // Requesting single batches should give the same results.
    val run2 = backfila.createWetRun<PlayerOriginBackfill>()
    run2.batchSize = 2L
    run2.scanSize = 4L
    run2.computeCountLimit = 1
    val scan1 = run2.singleScan()
    val scan2 = run2.singleScan()
    val scan3 = run2.singleScan()

    assertThat(scan.batches[0]).isEqualTo(scan1.batches.single())
    assertThat(scan.batches[1]).isEqualTo(scan2.batches.single())
    assertThat(scan.batches[2]).isEqualTo(scan3.batches.single())
  }

  @Test fun lessThanRequestedBatches() {
    val run = backfila.createWetRun<PlayerOriginBackfill>()
      .apply { configureForTest() }

    // Requested 20 batches but only 2 batches in the table.
    run.computeCountLimit = 20L
    run.singleScan()
    assertThat(run.batchesToRunSnapshot).hasSize(2)
  }

  @Test fun runOnAmericans() {
    val run = backfila.createWetRun<PlayerOriginBackfill>(
      parameterData = mapOf("originRegex" to "USA".encodeUtf8()),
    ).apply { configureForTest() }

    run.execute()
    assertThat(run.backfill.backfilledPlayers).hasSize(6)
  }

  @Test fun runOnAlphaCentauri() {
    val run = backfila.createWetRun<PlayerOriginBackfill>(
      parameterData = mapOf("originRegex" to "ALPHA_CENTAURI".encodeUtf8()),
    ).apply { configureForTest() }

    run.execute()
    assertThat(run.backfill.backfilledPlayers).isEmpty()
  }

  private fun BackfillRun<*>.configureForTest() {
    this.batchSize = 10L
    this.scanSize = 100L
    this.computeCountLimit = 1L
  }
}
