package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.persistence.TestHockeyData
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests that verify the BoundingRangeStrategy integration works correctly.
 * The existing PlayerOriginBackfillTest exercises the default strategy implicitly.
 * These tests verify the strategy abstraction is wired correctly.
 */
@MiskTest(startService = true)
open class BoundingRangeStrategyTest {
  @Suppress("unused")
  @MiskTestModule
  open val module: com.google.inject.Module = TestingModule()

  @Inject lateinit var backfila: Backfila

  @Inject lateinit var hockeyDataDatabase: HockeyDataDatabase

  @Inject lateinit var testData: TestHockeyData

  @BeforeEach
  fun insertTestData() {
    testData.insertAnaheimDucks()
  }

  @Test
  fun `default strategy computes correct bounding range`() {
    // The default strategy should work the same as before the refactor
    val run = backfila.createWetRun<PlayerOriginBackfill>()
    run.batchSize = 5L
    run.scanSize = 10L
    run.computeCountLimit = 1L

    // First scan should find records
    val scan = run.singleScan()
    assertThat(scan.batches).isNotEmpty()

    // The batch should have a valid range
    val batch = scan.batches.first()
    assertThat(batch.batch_range.start).isNotNull()
    assertThat(batch.batch_range.end).isNotNull()
  }

  @Test
  fun `default strategy handles empty table`() {
    // Clear all data
    hockeyDataDatabase.transaction {
      hockeyDataDatabase.hockeyPlayerQueries.selectAll().executeAsList().forEach {
        hockeyDataDatabase.hockeyPlayerQueries.deletePlayer(it.player_number)
      }
    }

    val run = backfila.createWetRun<PlayerOriginBackfill>()

    // Empty table should return null range
    assertThat(run.partitionProgressSnapshot.values.single().utf8RangeStart()).isNull()
    assertThat(run.partitionProgressSnapshot.values.single().utf8RangeEnd()).isNull()
  }

  @Test
  fun `backfill uses single partition by default`() {
    val run = backfila.createWetRun<PlayerOriginBackfill>()

    // Default UnshardedPartitionProvider should create single "only" partition
    assertThat(run.partitionProgressSnapshot.keys).containsExactly("only")
  }
}
