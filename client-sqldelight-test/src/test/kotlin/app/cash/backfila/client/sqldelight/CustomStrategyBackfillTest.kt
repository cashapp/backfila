package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayer
import app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayersBackfillRecordSourceConfig
import app.cash.backfila.client.sqldelight.persistence.TestHockeyData
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests that verify custom PartitionProvider and BoundingRangeStrategy implementations
 * can be used with SqlDelightDatasourceBackfill.
 */
@MiskTest(startService = true)
class CustomStrategyBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module = CustomStrategyTestingModule()

  @Inject lateinit var backfila: Backfila

  @Inject lateinit var hockeyDataDatabase: HockeyDataDatabase

  @Inject lateinit var testData: TestHockeyData

  @Inject lateinit var trackingPartitionProvider: TrackingPartitionProvider

  @Inject lateinit var trackingStrategy: TrackingBoundingRangeStrategy

  @BeforeEach
  fun setUp() {
    testData.insertAnaheimDucks()
    trackingPartitionProvider.reset()
    trackingStrategy.reset()
  }

  @Test
  fun `custom partition provider is used`() {
    backfila.createWetRun<CustomStrategyBackfill>()

    // The tracking partition provider should have been called
    assertThat(trackingPartitionProvider.namesCallCount.get()).isGreaterThan(0)
  }

  @Test
  fun `custom bounding range strategy is used during scan`() {
    val run = backfila.createWetRun<CustomStrategyBackfill>()
    run.batchSize = 5L
    run.scanSize = 10L
    run.computeCountLimit = 1L

    // Perform a scan which should invoke the bounding range strategy
    run.singleScan()

    // The tracking strategy should have been called
    assertThat(trackingStrategy.computeBoundingRangeMaxCallCount.get()).isGreaterThan(0)
  }
}

/**
 * A partition provider that tracks calls for testing.
 */
@Singleton
class TrackingPartitionProvider @Inject constructor(
  val trackingStrategy: TrackingBoundingRangeStrategy,
) : PartitionProvider {
  val namesCallCount = AtomicInteger(0)
  val transactionCallCount = AtomicInteger(0)

  fun reset() {
    namesCallCount.set(0)
    transactionCallCount.set(0)
  }

  override fun names(request: PrepareBackfillRequest): List<String> {
    namesCallCount.incrementAndGet()
    return listOf("only")
  }

  override fun <T> transaction(partitionName: String, task: () -> T): T {
    transactionCallCount.incrementAndGet()
    return task()
  }

  @Suppress("UNCHECKED_CAST")
  override fun <K : Any> boundingRangeStrategy(): BoundingRangeStrategy<K> =
    trackingStrategy as BoundingRangeStrategy<K>
}

/**
 * A bounding range strategy that tracks calls and delegates to the default.
 */
@Singleton
class TrackingBoundingRangeStrategy @Inject constructor() : BoundingRangeStrategy<Int> {
  private val delegate = DefaultBoundingRangeStrategy<Int>()
  val computeAbsoluteRangeCallCount = AtomicInteger(0)
  val computeBoundingRangeMaxCallCount = AtomicInteger(0)

  fun reset() {
    computeAbsoluteRangeCallCount.set(0)
    computeBoundingRangeMaxCallCount.set(0)
  }

  override fun computeAbsoluteRange(
    partitionName: String,
    queries: SqlDelightRecordSourceConfig<Int, *>,
  ): MinMax<Int> {
    computeAbsoluteRangeCallCount.incrementAndGet()
    return delegate.computeAbsoluteRange(partitionName, queries)
  }

  override fun computeBoundingRangeMax(
    partitionName: String,
    previousEndKey: Int?,
    rangeStart: Int,
    rangeEnd: Int,
    scanSize: Long,
    queries: SqlDelightRecordSourceConfig<Int, *>,
  ): Int? {
    computeBoundingRangeMaxCallCount.incrementAndGet()
    return delegate.computeBoundingRangeMax(partitionName, previousEndKey, rangeStart, rangeEnd, scanSize, queries)
  }
}

/**
 * A backfill that uses custom partition provider and bounding range strategy.
 */
@Singleton
class CustomStrategyBackfill @Inject constructor(
  hockeyDataDatabase: HockeyDataDatabase,
  private val trackingPartitionProvider: TrackingPartitionProvider,
) : SqlDelightDatasourceBackfill<Int, HockeyPlayer, NoParameters>(
  recordSourceConfig = HockeyPlayersBackfillRecordSourceConfig(hockeyDataDatabase),
) {
  val backfilledPlayers = mutableListOf<HockeyPlayer>()

  override fun partitionProvider(): PartitionProvider = trackingPartitionProvider

  override fun runBatch(records: List<HockeyPlayer>, config: BackfillConfig<NoParameters>) {
    backfilledPlayers.addAll(records)
  }
}
