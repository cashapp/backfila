package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.embedded.Backfila
import app.cash.backfila.client.misk.embedded.BackfillRun
import app.cash.backfila.client.misk.setup.ClientJooqTestingModule
import app.cash.backfila.client.misk.setup.CompoundKey
import app.cash.backfila.client.misk.setup.JooqDBIdentifier
import app.cash.backfila.client.misk.setup.JooqMenuTestBackfill
import app.cash.backfila.client.misk.setup.JooqTransacter
import app.cash.backfila.client.misk.setup.JooqWidgetCompoundKeyBackfill
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.tokens.TokenGenerator
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * This test is good in a way that it avoids duplication, but it could get a little complicated to
 * setup. The test will work only work if all the backfills have data set up in this manner:
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

  @TestFactory
  fun emptyTable() = listOf(
    JooqBackfillTestOptions<Long, JooqMenuTestBackfill>(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = emptyList(),
      backfila
    ),
    JooqBackfillTestOptions<CompoundKey, JooqWidgetCompoundKeyBackfill>(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = emptyList(),
      backfila
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("empty table test ${backfillOption.backfillClass.simpleName}") {
      val run = backfillOption.createDryRun().apply { configureForTest() }
      assertThat(run.partitionProgressSnapshot.values.single().utf8RangeStart()).isNull()
      assertThat(run.partitionProgressSnapshot.values.single().utf8RangeEnd()).isNull()
      // Trying to scan for a batch on an empty tablet gets nothing to execute.
      assertThat(run.singleScan().batches).isEmpty() // Check the returned scan
      app.cash.backfila.client.misk.testing.assertThat(run).isFinishedScanning()
        .hasNoBatchesToRun()
        .isComplete()
    }
  }

  @TestFactory
  fun noMatchTests() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createNoMatching(transacter),
      backfila
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createNoMatching(transacter, clock, tokenGenerator),
      backfila
    )
  ).map { backfillTestOption ->
    DynamicTest.dynamicTest("no match test ${backfillTestOption.backfillClass.simpleName}") {
      val run = backfillTestOption.createDryRun().apply { configureForTest() }
      assertThat(run.partitionProgressSnapshot.values.single().utf8RangeStart()).isNotNull()
      assertThat(run.partitionProgressSnapshot.values.single().utf8RangeEnd()).isNotNull()
      assertThat(run.partitionProgressSnapshot.values.single().previousEndKey).isNull()

      val scan1 = run.singleScan()
      assertThat(scan1.batches).size().isEqualTo(1)
      assertThat(scan1.batches.single().scanned_record_count).isEqualTo(5)
      assertThat(scan1.batches.single().matching_record_count).isEqualTo(0)
      app.cash.backfila.client.misk.testing.assertThat(run.partitionProgressSnapshot.values.single())
        .isNotDone()
      assertThat(run.partitionProgressSnapshot.values.single().previousEndKey).isNotNull

      val scan2 = run.singleScan()
      assertThat(scan2.batches).isEmpty()
      app.cash.backfila.client.misk.testing.assertThat(run.partitionProgressSnapshot.values.single())
        .isDone()
      app.cash.backfila.client.misk.testing.assertThat(run).hasNoBatchesToRun().isComplete()
    }
  }

  @TestFactory
  fun withStartRange() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createSome(transacter),
      backfila
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createSome(transacter, clock, tokenGenerator),
      backfila
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("with start range test ${backfillOption.backfillClass.simpleName}") {
      // Start at the 2nd id, so the 1st should be skipped.
      val run = backfillOption.createDryRun(rangeStart = backfillOption.backfillRowKeys[1].toString())
        .apply { configureForTest() }
      assertThat(run.rangeStart).isEqualTo(backfillOption.backfillRowKeys[1].toString())
      app.cash.backfila.client.misk.testing.assertThat(run.partitionProgressSnapshot.values.single())
        .isNotDone()

      run.singleScan()
      assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo(
        backfillOption.backfillRowKeys[1].toString()
      )
      assertThat(run.partitionProgressSnapshot.values.single().utf8PreviousEndKey()).isEqualTo(
        backfillOption.backfillRowKeys[10].toString()
      )

      run.execute()
      assertThat(run.backfill.idsRanDry).containsExactlyElementsOf(backfillOption.backfillRowKeys.slice(1..19))
      assertThat(run.backfill.idsRanWet).isEmpty()
    }
  }

  @TestFactory
  fun withEndRange() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createSome(transacter),
      backfila
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createSome(transacter, clock, tokenGenerator),
      backfila
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("with end range test ${backfillOption.backfillClass.simpleName}") {
      // End after the 1st id, so only the first id should get backfilled.
      val run = backfillOption.createDryRun(
        rangeEnd = backfillOption.backfillRowKeys[0].toString()
      )
        .apply { configureForTest() }
      assertThat(run.rangeEnd).isEqualTo(backfillOption.backfillRowKeys[0].toString())
      app.cash.backfila.client.misk.testing.assertThat(run.partitionProgressSnapshot.values.single())
        .isNotDone()

      run.singleScan()
      assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo(
        backfillOption.backfillRowKeys[0].toString()
      )
      assertThat(run.batchesToRunSnapshot.single().matchingRecordCount).isEqualTo(1)
      assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(1)
      run.runBatch()

      run.singleScan()
      app.cash.backfila.client.misk.testing.assertThat(run).hasNoBatchesToRun()
      app.cash.backfila.client.misk.testing.assertThat(run).isFinishedScanning()

      app.cash.backfila.client.misk.testing.assertThat(run).isComplete()
      assertThat(run.backfill.idsRanDry).containsExactly(backfillOption.backfillRowKeys[0])
      assertThat(run.backfill.idsRanWet).isEmpty()
    }
  }

  @TestFactory
  fun twoBatchesOf10ToGet20Records() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createSome(transacter),
      backfila
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createSome(transacter, clock, tokenGenerator),
      backfila
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("twoBatchesOf10ToGet20Records test ${backfillOption.backfillClass.simpleName}") {
      val run = backfillOption.createDryRun()
        .apply { configureForTest() }
      run.precomputeRemaining()

      run.singleScan()
      assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo(
        backfillOption.backfillRowKeys[0].toString()
      )
      assertThat(run.batchesToRunSnapshot.single().utf8RangeEnd()).isEqualTo(
        backfillOption.backfillRowKeys[9].toString()
      )
      assertThat(run.batchesToRunSnapshot.single().matchingRecordCount).isEqualTo(10)
      assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(10)
      run.runBatch()
      app.cash.backfila.client.misk.testing.assertThat(run).hasNoBatchesToRun()

      run.singleScan()
      assertThat(run.batchesToRunSnapshot.single().utf8RangeEnd()).isEqualTo(
        backfillOption.backfillRowKeys[19].toString()
      )
      assertThat(run.batchesToRunSnapshot.single().matchingRecordCount).isEqualTo(10)
      assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(
        15
      ) // Skipped 5 records in between
      run.runBatch()
      app.cash.backfila.client.misk.testing.assertThat(run).hasNoBatchesToRun()

      run.singleScan()
      app.cash.backfila.client.misk.testing.assertThat(run).hasNoBatchesToRun().isFinishedScanning()

      app.cash.backfila.client.misk.testing.assertThat(run).isComplete()
      assertThat(run.backfill.idsRanDry).containsExactlyElementsOf(backfillOption.backfillRowKeys)
      assertThat(run.backfill.idsRanWet).isEmpty()
    }
  }

  @TestFactory
  fun precomputingIgnoresBatchSize() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createSome(transacter),
      backfila
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createSome(transacter, clock, tokenGenerator),
      backfila
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("precomputingIgnoresBatchSize test ${backfillOption.backfillClass.simpleName}") {
      val run = backfillOption.createDryRun()
      // Setup a small batch and scan size
      run.batchSize = 2L
      run.scanSize = 10L
      run.computeCountLimit = 1L

      val scan1 = run.precomputeScan()
      assertThat(scan1.batches).size().isEqualTo(1)
      val batch1 = scan1.batches.single()
      assertThat(batch1.batch_range.start.utf8()).isEqualTo(backfillOption.backfillRowKeys[0].toString())
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
      app.cash.backfila.client.misk.testing.assertThat(run).isFinishedPrecomputing()

      assertThat(run.precomputeMatchingCount).isEqualTo(20)
      assertThat(run.precomputeScannedCount).isEqualTo(25)

      // Batches don't get added when precomputing
      app.cash.backfila.client.misk.testing.assertThat(run).hasNoBatchesToRun()
      // Nor is there progress on the cursor
      app.cash.backfila.client.misk.testing.assertThat(run.partitionProgressSnapshot.values.single())
        .isNotDone()
      assertThat(run.partitionProgressSnapshot.values.single().previousEndKey).isNull()
      // Nor is it complete
      app.cash.backfila.client.misk.testing.assertThat(run).isNotComplete()
    }
  }

  @TestFactory
  fun multipleBatches() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createSome(transacter),
      backfila
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createSome(transacter, clock, tokenGenerator),
      backfila
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("multipleBatches test ${backfillOption.backfillClass.simpleName}") {
      val run1 = backfillOption.createDryRun()
        .apply { configureForTest() }

      run1.computeCountLimit = 2
      val scan = run1.singleScan()
      assertThat(scan.batches).size().isEqualTo(2)
      assertThat(scan.batches[0].batch_range.end).isLessThan(scan.batches[1].batch_range.start)

      // Requesting two batches should give the same batches as requesting one twice.
      val run2 = backfillOption.createDryRun()
        .apply { configureForTest() }
      val scan1 = run2.singleScan()
      val scan2 = run2.singleScan()

      assertThat(scan.batches[0]).isEqualTo(scan1.batches.single())
      assertThat(scan.batches[1]).isEqualTo(scan2.batches.single())
    }
  }

  @TestFactory
  fun multipleScans() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createSome(transacter),
      backfila
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createSome(transacter, clock, tokenGenerator),
      backfila
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("multipleScans test ${backfillOption.backfillClass.simpleName}") {
      val run1 = backfillOption.createDryRun()
      run1.batchSize = 2L
      run1.scanSize = 4L
      run1.computeCountLimit = 3
      val scan = run1.singleScan()
      assertThat(scan.batches).size().isEqualTo(3)
      assertThat(scan.batches[0].batch_range.end).isLessThan(scan.batches[1].batch_range.start)
      assertThat(scan.batches[1].batch_range.end).isLessThan(scan.batches[2].batch_range.start)

      // Requesting single batches should give the same results.
      val run2 = backfillOption.createDryRun()
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
  }

  @TestFactory
  fun lessThanRequestedBatches() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createSome(transacter),
      backfila
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createSome(transacter, clock, tokenGenerator),
      backfila
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("lessThanRequestedBatches test ${backfillOption.backfillClass.simpleName}") {
      val run = backfillOption.createDryRun()
        .apply { configureForTest() }

      // Requested 20 batches but only 2 batches in the table.
      run.computeCountLimit = 20L
      run.singleScan()
      assertThat(run.batchesToRunSnapshot).hasSize(2)
      assertThat(run.batchesToRunSnapshot).allMatch { it.matchingRecordCount == 10L }
    }
  }

  @TestFactory
  fun wetAndDryRunProcessSameElements() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createSome(transacter),
      backfila
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createSome(transacter, clock, tokenGenerator),
      backfila
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("wetAndDryRunProcessSameElements test ${backfillOption.backfillClass.simpleName}") {
      val dryRun = backfillOption.createDryRun()
        .apply { configureForTest() }
      dryRun.execute()
      app.cash.backfila.client.misk.testing.assertThat(dryRun).isComplete()
      assertThat(dryRun.backfill.idsRanWet).isEmpty()

      val wetRun = backfillOption.createWetRun()
        .apply { configureForTest() }
      wetRun.execute()
      app.cash.backfila.client.misk.testing.assertThat(wetRun).isComplete()
      assertThat(wetRun.backfill.idsRanDry).isEmpty()

      assertThat(dryRun.backfill.idsRanDry).containsExactlyElementsOf(wetRun.backfill.idsRanWet)
    }
  }

  @TestFactory
  fun checkForDescriptions() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createSome(transacter),
      backfila,
      description = "So we can backfill menus."
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createSome(transacter, clock, tokenGenerator),
      backfila,
      description = "So we can backfill widgets."
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("checkForDescriptions test ${backfillOption.backfillClass.simpleName}") {
      val dryRun = backfillOption.createDryRun()
        .apply { configureForTest() }
      dryRun.execute()
      app.cash.backfila.client.misk.testing.assertThat(dryRun).isComplete()
      assertThat(dryRun.backfill.idsRanWet).isEmpty()

      val wetRun = backfillOption.createWetRun()
        .apply { configureForTest() }
      wetRun.execute()
      app.cash.backfila.client.misk.testing.assertThat(wetRun).isComplete()
      assertThat(wetRun.backfill.idsRanDry).isEmpty()

      assertThat(dryRun.backfill.idsRanDry).containsExactlyElementsOf(wetRun.backfill.idsRanWet)
    }
  }

  @TestFactory
  fun checkParameters() = listOf(
    JooqBackfillTestOptions(
      backfillClass = JooqMenuTestBackfill::class,
      backfillRowKeys = JooqMenuBackfillDbSetup.createSome(transacter),
      backfila,
      parameterData = mapOf("type" to "beef".encodeUtf8())
    ),
    JooqBackfillTestOptions(
      backfillClass = JooqWidgetCompoundKeyBackfill::class,
      backfillRowKeys = WidgetCompoundKeyBackfillDbSetup.createSome(transacter, clock, tokenGenerator),
      backfila,
      parameterData = mapOf("manufacturerTokensCSV" to "token2 - not select for backfill".encodeUtf8())
    )
  ).map { backfillOption ->
    DynamicTest.dynamicTest("checkParameters test ${backfillOption.backfillClass.simpleName}") {
      val run = backfillOption.createDryRun().apply { configureForTest() }

      run.execute()
      assertThat(run.backfill.idsRanDry).size().isEqualTo(5)
      assertThat(run.backfill.idsRanWet).isEmpty()
    }
  }
}

private fun BackfillRun<*>.configureForTest() {
  this.batchSize = 10L
  this.scanSize = 100L
  this.computeCountLimit = 1L
}

private data class JooqBackfillTestOptions<K : Any, BackfillType : Backfill>(
  val backfillClass: KClass<BackfillType>,
  val backfillRowKeys: List<K>,
  val backfila: Backfila,
  val description: String = "",
  val parameterData: Map<String, ByteString> = mapOf()
) {
  fun createDryRun(
    rangeStart: String? = null,
    rangeEnd: String? = null
  ): BackfillRun<BackfillType> {
    return backfila.createDryRun(backfillClass, parameterData, rangeStart, rangeEnd)
  }

  fun createWetRun(
    rangeStart: String? = null,
    rangeEnd: String? = null
  ): BackfillRun<BackfillType> {
    return backfila.createWetRun(backfillClass, parameterData, rangeStart, rangeEnd)
  }
}
