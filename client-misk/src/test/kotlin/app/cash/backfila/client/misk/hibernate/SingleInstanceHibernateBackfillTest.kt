package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.client.misk.UnshardedInstanceProvider
import app.cash.backfila.client.misk.embedded.Backfila
import app.cash.backfila.client.misk.embedded.BackfillRun
import app.cash.backfila.client.misk.embedded.createDryRun
import app.cash.backfila.client.misk.embedded.createWetRun
import app.cash.backfila.client.misk.testing.assertThat
import javax.inject.Inject
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import okhttp3.internal.toImmutableList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

abstract class SingleInstanceHibernateBackfillTest {
  @Inject @ClientMiskService lateinit var transacter: Transacter
  @Inject lateinit var backfila: Backfila

  protected class TestBackfill @Inject constructor(
    @ClientMiskService private val transacter: Transacter,
    private val queryFactory: Query.Factory
  ) : Backfill<DbMenu, Id<DbMenu>>() {
    val idsRanDry = mutableListOf<Id<DbMenu>>()
    val idsRanWet = mutableListOf<Id<DbMenu>>()

    override fun backfillCriteria(config: BackfillConfig): Query<DbMenu> {
      return queryFactory.newQuery(MenuQuery::class).name("chicken")
    }

    override fun runBatch(pkeys: List<Id<DbMenu>>, config: BackfillConfig) {
      if (config.dryRun) {
        idsRanDry.addAll(pkeys)
      } else {
        idsRanWet.addAll(pkeys)
      }
    }

    override fun instanceProvider() = UnshardedInstanceProvider(transacter)
  }

  @Test fun emptyTable() {
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }
    assertThat(run.instanceProgressSnapshot.values.single().utf8RangeStart()).isNull()
    assertThat(run.instanceProgressSnapshot.values.single().utf8RangeEnd()).isNull()
    // Trying to scan for a batch on an empty tablet gets nothing to execute.
    assertThat(run.singleScan().batches).isEmpty() // Check the returned scan
    assertThat(run).isFinishedScanning()
        .hasNoBatchesToRun()
        .isComplete()
  }

  @Test fun noMatches() {
    createNoMatching()
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }
    assertThat(run.instanceProgressSnapshot.values.single().utf8RangeStart()).isNotNull()
    assertThat(run.instanceProgressSnapshot.values.single().utf8RangeEnd()).isNotNull()
    assertThat(run.instanceProgressSnapshot.values.single().previousEndKey).isNull()

    val scan1 = run.singleScan()
    assertThat(scan1.batches).size().isEqualTo(1)
    assertThat(scan1.batches.single().scanned_record_count).isEqualTo(5)
    assertThat(scan1.batches.single().matching_record_count).isEqualTo(0)
    assertThat(run.instanceProgressSnapshot.values.single()).isNotDone()
    assertThat(run.instanceProgressSnapshot.values.single().previousEndKey).isNotNull

    val scan2 = run.singleScan()
    assertThat(scan2.batches).isEmpty()
    assertThat(run.instanceProgressSnapshot.values.single()).isDone()
    assertThat(run).hasNoBatchesToRun().isComplete()
  }

  @Test fun withStartRange() {
    val expectedIds = createSome()
    // Start at the 2nd id, so the 1st should be skipped.
    val run = backfila.createDryRun<TestBackfill>(rangeStart = expectedIds[1].toString())
        .apply { configureForTest() }
    assertThat(run.rangeStart).isEqualTo(expectedIds[1].toString())
    assertThat(run.instanceProgressSnapshot.values.single()).isNotDone()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo(
        expectedIds[1].toString())
    assertThat(run.instanceProgressSnapshot.values.single().utf8PreviousEndKey()).isEqualTo(
        expectedIds[10].toString())

    run.execute()
    assertThat(run.backfill.idsRanDry).containsExactlyElementsOf(expectedIds.slice(1..19))
    assertThat(run.backfill.idsRanWet).isEmpty()
  }

  @Test fun withEndRange() {
    val expectedIds = createSome()
    // End after the 1st id, so only the first id should get backfilled.
    val run = backfila.createDryRun<TestBackfill>(rangeEnd = expectedIds[0].toString())
        .apply { configureForTest() }
    assertThat(run.rangeEnd).isEqualTo(expectedIds[0].toString())
    assertThat(run.instanceProgressSnapshot.values.single()).isNotDone()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo(
        expectedIds[0].toString())
    assertThat(run.batchesToRunSnapshot.single().matchingRecordCount).isEqualTo(1)
    assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(1)
    run.runBatch()

    run.singleScan()
    assertThat(run).hasNoBatchesToRun()
    assertThat(run).isFinishedScanning()

    assertThat(run).isComplete()
    assertThat(run.backfill.idsRanDry).containsExactly(expectedIds[0])
    assertThat(run.backfill.idsRanWet).isEmpty()
  }

  @Test fun twoBatchesOf10ToGet20Records() {
    val expectedIds = createSome()
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }
    run.precomputeRemaining()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo(
        expectedIds[0].toString())
    assertThat(run.batchesToRunSnapshot.single().utf8RangeEnd()).isEqualTo(
        expectedIds[9].toString())
    assertThat(run.batchesToRunSnapshot.single().matchingRecordCount).isEqualTo(10)
    assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(10)
    run.runBatch()
    assertThat(run).hasNoBatchesToRun()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeEnd()).isEqualTo(
        expectedIds[19].toString())
    assertThat(run.batchesToRunSnapshot.single().matchingRecordCount).isEqualTo(10)
    assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(
        15) // Skipped some `beef`
    run.runBatch()
    assertThat(run).hasNoBatchesToRun()

    run.singleScan()
    assertThat(run).hasNoBatchesToRun().isFinishedScanning()

    assertThat(run).isComplete()
    assertThat(run.backfill.idsRanDry).containsExactlyElementsOf(expectedIds)
    assertThat(run.backfill.idsRanWet).isEmpty()
  }

  @Test fun precomputingIgnoresBatchSize() {
    val expectedIds = createSome()
    val run = backfila.createDryRun<TestBackfill>()
    // Setup a small batch and scan size
    run.batchSize = 2L
    run.scanSize = 10L
    run.computeCountLimit = 1L

    val scan1 = run.precomputeScan()
    assertThat(scan1.batches).size().isEqualTo(1)
    val batch1 = scan1.batches.single()
    assertThat(batch1.batch_range.start.utf8()).isEqualTo(expectedIds[0].toString())
    assertThat(batch1.matching_record_count).isEqualTo(10)
    assertThat(batch1.scanned_record_count).isEqualTo(10)

    run.scanSize = 20L
    val scan2 = run.precomputeScan()
    assertThat(scan2.batches).size().isEqualTo(1)
    val batch2 = scan2.batches.single()
    assertThat(batch2.matching_record_count).isEqualTo(10)
    // 5 extra were scanned and skipped, because they were interspersed.
    assertThat(batch2.scanned_record_count).isEqualTo(15)

    run.scanSize = 100L
    val scan3 = run.precomputeScan()
    assertThat(scan3.batches).isEmpty()
    assertThat(run).isFinishedPrecomputing()

    assertThat(run.precomputeMatchingCount).isEqualTo(20)
    assertThat(run.precomputeScannedCount).isEqualTo(25)

    // Batches don't get added when precomputing
    assertThat(run).hasNoBatchesToRun()
    // Nor is there progress on the cursor
    assertThat(run.instanceProgressSnapshot.values.single()).isNotDone()
    assertThat(run.instanceProgressSnapshot.values.single().previousEndKey).isNull()
    // Nor is it complete
    assertThat(run).isNotComplete()
  }

  @Test fun multipleBatches() {
    createSome()
    val run1 = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }

    run1.computeCountLimit = 2
    val scan = run1.singleScan()
    assertThat(scan.batches).size().isEqualTo(2)
    assertThat(scan.batches[0].batch_range.end).isLessThan(scan.batches[1].batch_range.start)

    // Requesting two batches should give the same batches as requesting one twice.
    val run2 = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }
    val scan1 = run2.singleScan()
    val scan2 = run2.singleScan()

    assertThat(scan.batches[0]).isEqualTo(scan1.batches.single())
    assertThat(scan.batches[1]).isEqualTo(scan2.batches.single())
  }

  @Test fun multipleScans() {
    createSome()
    val run1 = backfila.createDryRun<TestBackfill>()
    run1.batchSize = 2L
    run1.scanSize = 4L
    run1.computeCountLimit = 3
    val scan = run1.singleScan()
    assertThat(scan.batches).size().isEqualTo(3)
    assertThat(scan.batches[0].batch_range.end).isLessThan(scan.batches[1].batch_range.start)
    assertThat(scan.batches[1].batch_range.end).isLessThan(scan.batches[2].batch_range.start)

    // Requesting single batches should give the same results.
    val run2 = backfila.createDryRun<TestBackfill>()
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
    createSome()
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }

    // Requested 20 batches but only 2 batches in the table.
    run.computeCountLimit = 20L
    run.singleScan()
    assertThat(run.batchesToRunSnapshot).hasSize(2)
    assertThat(run.batchesToRunSnapshot).allMatch { it.matchingRecordCount == 10L }
  }

  @Test fun wetAndDryRunProcessSameElements() {
    createSome()
    val dryRun = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }
    dryRun.execute()
    assertThat(dryRun).isComplete()
    assertThat(dryRun.backfill.idsRanWet).isEmpty()

    val wetRun = backfila.createWetRun<TestBackfill>()
        .apply { configureForTest() }
    wetRun.execute()
    assertThat(wetRun).isComplete()
    assertThat(wetRun.backfill.idsRanDry).isEmpty()

    assertThat(dryRun.backfill.idsRanDry).containsExactlyElementsOf(wetRun.backfill.idsRanWet)
  }

  private fun BackfillRun<*>.configureForTest() {
    this.batchSize = 10L
    this.scanSize = 100L
    this.computeCountLimit = 1L
  }

  private fun createSome(): List<Id<DbMenu>> {
    return transacter.transaction { session: Session ->
      val expected = mutableListOf<Id<DbMenu>>()
      repeat((1..10).count()) {
        val id = session.save(DbMenu("chicken"))
        expected.add(id)
      }

      // Intersperse these to make sure we test skipping non matching records.
      repeat((1..5).count()) { session.save(DbMenu("beef")) }

      repeat((1..10).count()) {
        val id = session.save(DbMenu("chicken"))
        expected.add(id)
      }
      expected.toImmutableList()
    }
  }

  private fun createNoMatching() {
    transacter.transaction { session: Session ->
      repeat((1..5).count()) { session.save(DbMenu("beef")) }
    }
  }
}
