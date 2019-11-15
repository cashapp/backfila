package app.cash.backfila.client.misk.base

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.client.misk.UnshardedInstanceProvider
import app.cash.backfila.client.misk.embedded.Backfila
import app.cash.backfila.client.misk.embedded.BackfillRun
import app.cash.backfila.client.misk.embedded.createDryRun
import com.google.inject.Module
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.internal.toImmutableList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@MiskTest(startService = true)
class UnshardedHibernateBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(false, listOf(TestBackfill::class))

  @Inject @ClientMiskService lateinit var transacter: Transacter
  @Inject lateinit var backfila: Backfila

  // (pkeySqlAdapter, queryFactory, transacter)

  fun newBackfill() {

  }

  class TestBackfill @Inject constructor(
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
    assertEquals(run.prepareBackfillResponse.instances.size, 1)
    assertNull(run.instanceProgressSnapshot.values.single().keyRange.start)
    // Trying to get a batch on an empty tablet gets nothing to execute.
    assertTrue { run.singleScan().batches.isEmpty() }
    assertTrue { run.complete() }
  }

  @Test fun noMatches() {
    createNoMatching()
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }
    assertNotNull(run.instanceProgressSnapshot.values.single().keyRange.start)
    assertNull(run.instanceProgressSnapshot.values.single().previousEndKey)

    val scan1 = run.singleScan()
    assertEquals(scan1.batches.size, 1)
    assertEquals(scan1.batches.single().scanned_record_count, 5)
    assertEquals(scan1.batches.single().matching_record_count, 0)
    assertFalse { run.instanceProgressSnapshot.values.single().done }
    assertNotNull(run.instanceProgressSnapshot.values.single().previousEndKey)

    val scan2 = run.singleScan()
    assertTrue { scan2.batches.isEmpty() }
    assertTrue { run.instanceProgressSnapshot.values.single().done }
    assertTrue { run.complete() }
  }

  @Test fun withStartRange() {
    val expectedIds = createSome()
    // Start at the 2nd id, so the 1st should be skipped.
    val run = backfila.createDryRun<TestBackfill>(rangeStart = expectedIds[1].toString())
        .apply { configureForTest() }
    assertEquals(run.rangeStart, expectedIds[1].toString())
    assertEquals(run.prepareBackfillResponse.instances.size, 1)

    val scan1 = run.singleScan()
    assertEquals(scan1.batches.size, 1)
    assertEquals(scan1.batches.single().batch_range.start.utf8(), expectedIds[1].toString())

    run.execute()
    assertThat(run.backfill.idsRanDry).containsExactlyElementsOf(expectedIds.slice(1..19))
  }

  @Test fun withEndRange() {
    val expectedIds = createSome()
    // End after the 1st id, so only the first id should get backfilled.
    val run = backfila.createDryRun<TestBackfill>(rangeEnd = expectedIds[0].toString())
        .apply { configureForTest() }
    assertEquals(run.rangeEnd, expectedIds[0].toString())
    assertEquals(run.prepareBackfillResponse.instances.size, 1)

    val scan1 = run.singleScan()
    assertEquals(scan1.batches.size, 1)
    val batch1 = scan1.batches.single()
    assertEquals(batch1.batch_range.start.utf8(), expectedIds[0].toString())
    assertEquals(batch1.matching_record_count, 1)
    assertEquals(batch1.scanned_record_count, 1)

    val scan2 = run.singleScan()
    assertTrue { scan2.batches.isEmpty() }
    assertTrue { run.finishedScanning() }

    run.runAllScanned()
    assertTrue { run.complete() }
    assertThat(run.backfill.idsRanDry).containsExactly(expectedIds[0])
  }

  @Test fun twoBatchesOf10ToGet20Records() {
    val expectedIds = createSome()
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }

    val scan1 = run.singleScan()
    assertEquals(scan1.batches.size, 1)
    val batch1 = scan1.batches.single()
    assertEquals(batch1.batch_range.start.utf8(), expectedIds[0].toString())
    assertEquals(batch1.matching_record_count, 10)
    assertEquals(batch1.scanned_record_count, 10)

    val scan2 = run.singleScan()
    assertEquals(scan2.batches.size, 1)
    val batch2 = scan2.batches.single()
    assertEquals(batch2.matching_record_count, 10)
    assertEquals(batch2.scanned_record_count, 15) // Skipped some `beef`

    val scan3 = run.singleScan()
    assertTrue { scan3.batches.isEmpty() }
    assertTrue { run.finishedScanning() }

    run.runAllScanned()
    assertTrue { run.complete() }
    assertThat(run.backfill.idsRanDry).containsExactlyElementsOf(expectedIds)
  }

  @Test fun precomputingIgnoresBatchSize() {
    val expectedIds = createSome()
    val run = backfila.createDryRun<TestBackfill>()
    // Setup a small batch and scan size
    run.batchSize = 2L
    run.scanSize = 10L
    run.computeCountLimit = 1L

    val scan1 = run.precomputeScan()
    assertEquals(scan1.batches.size, 1)
    val batch1 = scan1.batches.single()
    assertEquals(batch1.batch_range.start.utf8(), expectedIds[0].toString())
    assertEquals(batch1.matching_record_count, 10)
    assertEquals(batch1.scanned_record_count, 10)

    run.scanSize = 20L
    val scan2 = run.precomputeScan()
    assertEquals(scan2.batches.size, 1)
    val batch2 = scan2.batches.single()
    assertEquals(batch2.matching_record_count, 10)
    // 5 extra were scanned and skipped, because they were interspersed.
    assertEquals(batch2.scanned_record_count, 15)

    run.scanSize = 100L
    val scan3 = run.precomputeScan()
    assertTrue { scan3.batches.isEmpty() }
    assertTrue { run.finishedPrecomputing() }

    assertEquals(run.precomputeMatchingCount, 20)
    assertEquals(run.precomputeScannedCount, 25)
  }

  @Test fun multipleBatches() {
    createSome()
    val run1 = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }

    run1.computeCountLimit = 2
    val scan = run1.singleScan()
    assertEquals(scan.batches.size, 2)
    assertThat(scan.batches[0].batch_range.end).isLessThan(scan.batches[1].batch_range.start)

    // Requesting two batches should give the same batches as requesting one twice.
    val run2 = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }
    val scan1 = run2.singleScan()
    val scan2 = run2.singleScan()

    assertEquals(scan.batches[0], scan1.batches.single())
    assertEquals(scan.batches[1], scan2.batches.single())
  }

  @Test fun multipleScans() {
    createSome()
    val run1 = backfila.createDryRun<TestBackfill>()
    run1.batchSize = 2L
    run1.scanSize = 4L
    run1.computeCountLimit = 3
    val scan = run1.singleScan()
    assertEquals(scan.batches.size, 3)
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

    assertEquals(scan.batches[0], scan1.batches.single())
    assertEquals(scan.batches[1], scan2.batches.single())
    assertEquals(scan.batches[2], scan3.batches.single())
  }

  @Test fun lessThanRequestedBatches() {
    createSome()
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }

    // Requested 20 batches but only 2 batches in the table.
    run.computeCountLimit = 20L
    val scan = run.singleScan()
    assertEquals(scan.batches.size, 2)
  }

  private fun BackfillRun<*>.configureForTest() {
    this.batchSize = 10L
    this.scanSize = 100L
    this.computeCountLimit = 1L
  }

  private fun createSome(): List<Id<DbMenu>> {
    return transacter.transaction { session: Session ->
      val expected = mutableListOf<Id<DbMenu>>()
      repeat((0..9).count()) {
        val id = session.save(DbMenu("chicken"))
        expected.add(id)
      }

      // Intersperse these to make sure we test skipping non matching records.
      repeat((0..4).count()) { session.save(DbMenu("beef")) }

      repeat((0..9).count()) {
        val id = session.save(DbMenu("chicken"))
        expected.add(id)
      }
      expected.toImmutableList()
    }
  }

  private fun createNoMatching() {
    transacter.transaction { session: Session ->
      repeat((0..4).count()) { session.save(DbMenu("beef")) }
    }
  }
}