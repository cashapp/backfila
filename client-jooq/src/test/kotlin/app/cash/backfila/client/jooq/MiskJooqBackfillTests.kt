package app.cash.backfila.client.jooq

import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.BackfillRun
import app.cash.backfila.client.jooq.config.ClientJooqTestingModule
import app.cash.backfila.client.jooq.config.JooqDBIdentifier
import app.cash.backfila.client.jooq.config.JooqMenuTestBackfill
import app.cash.backfila.client.jooq.config.JooqTransacter
import app.cash.backfila.client.jooq.config.IdRecorder
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.tokens.TokenGenerator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import javax.inject.Inject
import app.cash.backfila.client.testing.assertThat as testingAssertThat

/**
 * This test is good in a way that it avoids duplication, but it could get a little complicated to
 * setup. The test will work only work if all the backfills have data set up in the same manner:
 * For example most of the tests in this class creates tests like the below:
 * 10 records that match the backfill criteria
 * followed by 5 records that do not batch the criteria
 * followed by 10 records that match the criteria
 *
 * If you are creating any more backfills to test and would like to add them here, make sure to
 * follow the above test set up.
 */
@MiskTest(startService = true)
class MiskJooqBackfillTests {
  @MiskTestModule
  var module = ClientJooqTestingModule()

  @JooqDBIdentifier
  @Inject
  private lateinit var transacter: JooqTransacter

  @Inject
  private lateinit var backfila: Backfila

  @Inject
  private lateinit var clock: FakeClock

  @Inject
  private lateinit var tokenGenerator: TokenGenerator

  @ParameterizedTest(name = "empty table test - {0}")
  @MethodSource("app.cash.backfila.client.jooq.MiskJooqBackfillTestProviders#emptyTable")
  fun emptyTable(backfillOption: JooqBackfillTestOptions<*, *>) {
    backfillOption.backfillRowKeys(transacter, clock, tokenGenerator)
    val run = backfillOption.createDryRun(backfila).apply { configureForTest() }
    assertThat(run.partitionProgressSnapshot.values.single().utf8RangeStart()).isNull()
    assertThat(run.partitionProgressSnapshot.values.single().utf8RangeEnd()).isNull()
    // Trying to scan for a batch on an empty tablet gets nothing to execute.
    assertThat(run.singleScan().batches).isEmpty() // Check the returned scan
    testingAssertThat(run).isFinishedScanning()
      .hasNoBatchesToRun()
      .isComplete()
  }

  @ParameterizedTest(name = "no matching test - {0}")
  @MethodSource("app.cash.backfila.client.jooq.MiskJooqBackfillTestProviders#noMatching")
  fun noMatching(backfillOption: JooqBackfillTestOptions<*, *>) {
    backfillOption.backfillRowKeys(transacter, clock, tokenGenerator)
    val run = backfillOption.createDryRun(backfila).apply { configureForTest() }
    assertThat(run.partitionProgressSnapshot.values.single().utf8RangeStart()).isNotNull()
    assertThat(run.partitionProgressSnapshot.values.single().utf8RangeEnd()).isNotNull()
    assertThat(run.partitionProgressSnapshot.values.single().previousEndKey).isNull()

    val scan1 = run.singleScan()
    assertThat(scan1.batches).size().isEqualTo(1)
    assertThat(scan1.batches.single().scanned_record_count).isEqualTo(5)
    assertThat(scan1.batches.single().matching_record_count).isEqualTo(0)
    testingAssertThat(run.partitionProgressSnapshot.values.single())
      .isNotDone()
    assertThat(run.partitionProgressSnapshot.values.single().previousEndKey).isNotNull

    val scan2 = run.singleScan()
    assertThat(scan2.batches).isEmpty()
    testingAssertThat(run.partitionProgressSnapshot.values.single())
      .isDone()
    testingAssertThat(run).hasNoBatchesToRun().isComplete()
  }

  @ParameterizedTest(name = "withStartRange test - {0}")
  @MethodSource("app.cash.backfila.client.jooq.MiskJooqBackfillTestProviders#createSome")
  fun <K : Any, BackfillType : IdRecorder<K, *>> withStartRange(
    backfillOption: JooqBackfillTestOptions<K, BackfillType>
  ) {
    val backfillRowKeys = backfillOption.backfillRowKeys(transacter, clock, tokenGenerator)
    val run = backfillOption.createDryRun(
      backfila,
      rangeStart = backfillRowKeys[1].toString()
    )
      .apply { configureForTest() }
    assertThat(run.rangeStart).isEqualTo(backfillRowKeys[1].toString())
    testingAssertThat(run.partitionProgressSnapshot.values.single())
      .isNotDone()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo(
      backfillRowKeys[1].toString()
    )
    assertThat(run.partitionProgressSnapshot.values.single().utf8PreviousEndKey()).isEqualTo(
      backfillRowKeys[10].toString()
    )

    run.execute()
    assertThat(run.backfill.idsRanDry).containsExactlyElementsOf(
      backfillRowKeys.slice(
        1..19
      )
    )
    assertThat(run.backfill.idsRanWet).isEmpty()
  }

  @ParameterizedTest(name = "withEndRange test - {0}")
  @MethodSource("app.cash.backfila.client.jooq.MiskJooqBackfillTestProviders#createSome")
  fun <K : Any, BackfillType : IdRecorder<K, *>> withEndRange(
    backfillOption: JooqBackfillTestOptions<K, BackfillType>
  ) {
    val backfillRowKeys = backfillOption.backfillRowKeys(transacter, clock, tokenGenerator)
    // End after the 1st id, so only the first id should get backfilled.
    val run = backfillOption.createDryRun(
      backfila,
      rangeEnd = backfillRowKeys[0].toString()
    )
      .apply { configureForTest() }
    assertThat(run.rangeEnd).isEqualTo(backfillRowKeys[0].toString())
    testingAssertThat(run.partitionProgressSnapshot.values.single())
      .isNotDone()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo(
      backfillRowKeys[0].toString()
    )
    assertThat(run.batchesToRunSnapshot.single().matchingRecordCount).isEqualTo(1)
    assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(1)
    run.runBatch()

    run.singleScan()
    testingAssertThat(run).hasNoBatchesToRun()
    testingAssertThat(run).isFinishedScanning()

    testingAssertThat(run).isComplete()
    assertThat(run.backfill.idsRanDry).containsExactly(backfillRowKeys[0])
    assertThat(run.backfill.idsRanWet).isEmpty()
  }

  @ParameterizedTest(name = "twoBatchesOf10ToGet20Records test - {0}")
  @MethodSource("app.cash.backfila.client.jooq.MiskJooqBackfillTestProviders#createSome")
  fun <K : Any, BackfillType : IdRecorder<K, *>> twoBatchesOf10ToGet20Records(
    backfillOption: JooqBackfillTestOptions<K, BackfillType>
  ) {
    val backfillRowKeys = backfillOption.backfillRowKeys(transacter, clock, tokenGenerator)
    val run = backfillOption.createDryRun(backfila)
      .apply { configureForTest() }
    run.precomputeRemaining()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo(
      backfillRowKeys[0].toString()
    )
    assertThat(run.batchesToRunSnapshot.single().utf8RangeEnd()).isEqualTo(
      backfillRowKeys[9].toString()
    )
    assertThat(run.batchesToRunSnapshot.single().matchingRecordCount).isEqualTo(10)
    assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(10)
    run.runBatch()
    testingAssertThat(run).hasNoBatchesToRun()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeEnd()).isEqualTo(
      backfillRowKeys[19].toString()
    )
    assertThat(run.batchesToRunSnapshot.single().matchingRecordCount).isEqualTo(10)
    assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(
      15
    ) // Skipped 5 records in between
    run.runBatch()
    testingAssertThat(run).hasNoBatchesToRun()

    run.singleScan()
    testingAssertThat(run).hasNoBatchesToRun().isFinishedScanning()

    testingAssertThat(run).isComplete()
    assertThat(run.backfill.idsRanDry).containsExactlyElementsOf(backfillRowKeys)
    assertThat(run.backfill.idsRanWet).isEmpty()
  }

  @ParameterizedTest(name = "precomputingIgnoresBatchSize test - {0}")
  @MethodSource("app.cash.backfila.client.jooq.MiskJooqBackfillTestProviders#createSome")
  fun <K : Any, BackfillType : IdRecorder<K, *>> precomputingIgnoresBatchSize(
    backfillOption: JooqBackfillTestOptions<K, BackfillType>
  ) {
    val backfillRowKeys = backfillOption.backfillRowKeys(transacter, clock, tokenGenerator)
    val run = backfillOption.createDryRun(backfila)
    // Setup a small batch and scan size
    run.batchSize = 2L
    run.scanSize = 10L
    run.computeCountLimit = 1L

    val scan1 = run.precomputeScan()
    assertThat(scan1.batches).size().isEqualTo(1)
    val batch1 = scan1.batches.single()
    assertThat(batch1.batch_range.start.utf8()).isEqualTo(backfillRowKeys[0].toString())
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
    testingAssertThat(run).isFinishedPrecomputing()

    assertThat(run.precomputeMatchingCount).isEqualTo(20)
    assertThat(run.precomputeScannedCount).isEqualTo(25)

    // Batches don't get added when precomputing
    testingAssertThat(run).hasNoBatchesToRun()
    // Nor is there progress on the cursor
    testingAssertThat(run.partitionProgressSnapshot.values.single())
      .isNotDone()
    assertThat(run.partitionProgressSnapshot.values.single().previousEndKey).isNull()
    // Nor is it complete
    testingAssertThat(run).isNotComplete()
  }

  @ParameterizedTest(name = "multipleBatches test - {0}")
  @MethodSource("app.cash.backfila.client.jooq.MiskJooqBackfillTestProviders#createSome")
  fun <K : Any, BackfillType : IdRecorder<K, *>> multipleBatches(
    backfillOption: JooqBackfillTestOptions<K, BackfillType>
  ) {
    backfillOption.backfillRowKeys(transacter, clock, tokenGenerator)
    val run1 = backfillOption.createDryRun(backfila)
      .apply { configureForTest() }

    run1.computeCountLimit = 2
    val scan = run1.singleScan()
    assertThat(scan.batches).size().isEqualTo(2)
    assertThat(scan.batches[0].batch_range.end).isLessThan(scan.batches[1].batch_range.start)

    // Requesting two batches should give the same batches as requesting one twice.
    val run2 = backfillOption.createDryRun(backfila)
      .apply { configureForTest() }
    val scan1 = run2.singleScan()
    val scan2 = run2.singleScan()

    assertThat(scan.batches[0]).isEqualTo(scan1.batches.single())
    assertThat(scan.batches[1]).isEqualTo(scan2.batches.single())
  }

  @ParameterizedTest(name = "multipleScans test - {0}")
  @MethodSource("app.cash.backfila.client.jooq.MiskJooqBackfillTestProviders#createSome")
  fun <K : Any, BackfillType : IdRecorder<K, *>> multipleScans(
    backfillOption: JooqBackfillTestOptions<K, BackfillType>
  ) {
    backfillOption.backfillRowKeys(transacter, clock, tokenGenerator)
    val run1 = backfillOption.createDryRun(backfila)
    run1.batchSize = 2L
    run1.scanSize = 4L
    run1.computeCountLimit = 3
    val scan = run1.singleScan()
    assertThat(scan.batches).size().isEqualTo(3)
    assertThat(scan.batches[0].batch_range.end).isLessThan(scan.batches[1].batch_range.start)
    assertThat(scan.batches[1].batch_range.end).isLessThan(scan.batches[2].batch_range.start)

    // Requesting single batches should give the same results.
    val run2 = backfillOption.createDryRun(backfila)
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

  @ParameterizedTest(name = "lessThanRequestedBatches test - {0}")
  @MethodSource("app.cash.backfila.client.jooq.MiskJooqBackfillTestProviders#createSome")
  fun <K : Any, BackfillType : IdRecorder<K, *>> lessThanRequestedBatches(
    backfillOption: JooqBackfillTestOptions<K, BackfillType>
  ) {
    backfillOption.backfillRowKeys(transacter, clock, tokenGenerator)
    val run = backfillOption.createDryRun(backfila)
      .apply { configureForTest() }

    // Requested 20 batches but only 2 batches in the table.
    run.computeCountLimit = 20L
    run.singleScan()
    assertThat(run.batchesToRunSnapshot).hasSize(2)
    assertThat(run.batchesToRunSnapshot).allMatch { it.matchingRecordCount == 10L }
  }

  @ParameterizedTest(name = "wetAndDryRunProcessSameElements test - {0}")
  @MethodSource("app.cash.backfila.client.jooq.MiskJooqBackfillTestProviders#createSome")
  fun <K : Any, BackfillType : IdRecorder<K, *>> wetAndDryRunProcessSameElements(
    backfillOption: JooqBackfillTestOptions<K, BackfillType>
  ) {
    backfillOption.backfillRowKeys(transacter, clock, tokenGenerator)
    val dryRun = backfillOption.createDryRun(backfila)
      .apply { configureForTest() }
    dryRun.execute()
    testingAssertThat(dryRun).isComplete()
    assertThat(dryRun.backfill.idsRanWet).isEmpty()

    val wetRun = backfillOption.createWetRun(backfila)
      .apply { configureForTest() }
    wetRun.execute()
    testingAssertThat(wetRun).isComplete()
    assertThat(wetRun.backfill.idsRanDry).isEmpty()

    assertThat(dryRun.backfill.idsRanDry).containsExactlyElementsOf(wetRun.backfill.idsRanWet)
  }

  @Test
  fun checkForDescriptions() {
    val configData = backfila.configureServiceData!!.backfills
      .first { it.name == JooqMenuTestBackfill::class.java.canonicalName }
    assertThat(configData.description)
      .isEqualTo("So we can backfill menus.")
    assertThat(configData.parameters.single().description)
      .isEqualTo("The type of sandwich to backfill. e.g. chicken, beef")
  }

  private fun BackfillRun<*>.configureForTest() {
    this.batchSize = 10L
    this.scanSize = 100L
    this.computeCountLimit = 1L
  }
}
