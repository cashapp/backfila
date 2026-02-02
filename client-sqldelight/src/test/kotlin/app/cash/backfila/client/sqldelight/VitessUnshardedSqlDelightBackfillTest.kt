package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.persistence.TestHockeyData
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import javax.inject.Inject
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Runs SqlDelight backfill tests against a real Vitess instance.
 *
 * This verifies that SqlDelight backfills work correctly with Vitess's SQL dialect.
 * Note: This tests unsharded Vitess only; sharded Vitess testing requires a more complex setup.
 */
@MiskTest(startService = true)
class VitessUnshardedSqlDelightBackfillTest {
  companion object {
    private const val VITESS_PORT = 27603
  }

  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule(useVitess = true, vitessPort = VITESS_PORT)

  @Suppress("unused")
  @MiskExternalDependency
  private val dockerVitess = DockerVitess(
    containerName = "vitess_sqldelight_test_db",
    port = VITESS_PORT,
  )

  @Inject lateinit var backfila: Backfila

  @Inject lateinit var hockeyDataDatabase: HockeyDataDatabase

  @Inject lateinit var testData: TestHockeyData

  @BeforeEach
  fun insertTestData() {
    testData.insertAnaheimDucks()
  }

  @Test
  fun `vitess backfill computes correct bounding range`() {
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
  fun `vitess backfill handles empty table`() {
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
  fun `vitess backfill uses single partition`() {
    val run = backfila.createWetRun<PlayerOriginBackfill>()

    // Default UnshardedPartitionProvider should create single "only" partition
    assertThat(run.partitionProgressSnapshot.keys).containsExactly("only")
  }
}
