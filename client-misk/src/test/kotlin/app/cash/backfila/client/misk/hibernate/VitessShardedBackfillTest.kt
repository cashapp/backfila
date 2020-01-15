package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbOrder
import app.cash.backfila.client.misk.DbRestaurant
import app.cash.backfila.client.misk.OrderQuery
import app.cash.backfila.client.misk.VitessShardedInstanceProvider
import app.cash.backfila.client.misk.embedded.Backfila
import app.cash.backfila.client.misk.embedded.BackfillRun
import app.cash.backfila.client.misk.embedded.createDryRun
import app.cash.backfila.client.misk.embedded.createWetRun
import app.cash.backfila.client.misk.internal.InstanceCursor
import app.cash.backfila.client.misk.testing.assertThat
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import com.google.inject.Module
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Shard
import misk.hibernate.Transacter
import misk.hibernate.annotation.keyspace
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okhttp3.internal.toImmutableList
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class VitessShardedBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(true, listOf(TestBackfill::class))

  @Inject @ClientMiskService lateinit var transacter: Transacter
  @Inject lateinit var backfila: Backfila

  private class TestBackfill @Inject constructor(
    @ClientMiskService private val transacter: Transacter,
    private val queryFactory: Query.Factory
  ) : Backfill<DbOrder, Id<DbOrder>>() {
    val idsRanDry = mutableListOf<Id<DbOrder>>()
    val idsRanWet = mutableListOf<Id<DbOrder>>()

    override fun backfillCriteria(config: BackfillConfig): Query<DbOrder> {
      // TODO: Make parameters better. Probably via an annotated data class
      val name = (config.parameters["name"] ?: "chickfila".encodeUtf8()).utf8()
      return queryFactory.newQuery(OrderQuery::class).restaurantName(name)
    }

    override fun runBatch(pkeys: List<Id<DbOrder>>, config: BackfillConfig) {
      if (config.dryRun) {
        idsRanDry.addAll(pkeys)
      } else {
        idsRanWet.addAll(pkeys)
      }
    }

    override fun instanceProvider() = VitessShardedInstanceProvider(transacter, this)
  }

  @Test fun emptyTable() {
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }
    run.instanceProgressSnapshot.values.forEach {
      assertThat(it.utf8RangeStart()).isNull()
      assertThat(it.utf8RangeEnd()).isNull()
    }
    // Neither shard should return batches
    assertThat(run.shardScan(LEFT_SHARD).batches).isEmpty()
    assertThat(run.shardScan(RIGHT_SHARD).batches).isEmpty()
    assertThat(run).isFinishedScanning()
        .hasNoBatchesToRun()
        .isComplete()

    assertThat(run.backfill.idsRanDry).isEmpty()
    assertThat(run.backfill.idsRanWet).isEmpty()
  }

  @Test fun noMatches() {
    createNoMatchingOrders()
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }
    run.instanceProgressSnapshot.values.forEach {
      assertThat(it.utf8RangeStart()).isNotNull()
      assertThat(it.utf8RangeEnd()).isNotNull()
      assertThat(it.previousEndKey).isNull()
    }

    val leftScan1 = run.shardScan(LEFT_SHARD)
    assertThat(leftScan1.batches).size().isEqualTo(1)
    assertThat(leftScan1.batches.single().scanned_record_count).isEqualTo(5)
    assertThat(leftScan1.batches.single().matching_record_count).isEqualTo(0)

    val rightScan1 = run.shardScan(RIGHT_SHARD)
    assertThat(rightScan1.batches).size().isEqualTo(1)
    assertThat(rightScan1.batches.single().scanned_record_count).isEqualTo(2)
    assertThat(rightScan1.batches.single().matching_record_count).isEqualTo(0)

    run.instanceProgressSnapshot.values.forEach {
      assertThat(it).isNotDone()
      assertThat(it.previousEndKey).isNotNull()
    }

    // One shard can be done before another.
    assertThat(run.shardScan(LEFT_SHARD).batches).isEmpty()
    assertThat(run.instanceProgressSnapshot[LEFT_SHARD]).isDone()
    assertThat(run.instanceProgressSnapshot[RIGHT_SHARD]).isNotDone()
    assertThat(run).isNotComplete()

    // Once both batches return empty the backfill is complete
    assertThat(run.shardScan(RIGHT_SHARD).batches).isEmpty()
    assertThat(run.instanceProgressSnapshot[LEFT_SHARD]).isDone()
    assertThat(run.instanceProgressSnapshot[RIGHT_SHARD]).isDone()
    assertThat(run).hasNoBatchesToRun().isComplete()

    assertThat(run.backfill.idsRanDry).isEmpty()
    assertThat(run.backfill.idsRanWet).isEmpty()
  }

  @Test fun withStartRange() {
    val expectedIds = createSomeOrders()
    // Start at the 2nd id, so the 1st should be skipped.
    val run = backfila.createDryRun<TestBackfill>(rangeStart = expectedIds[1].toString())
        .apply { configureForTest() }
    assertThat(run.rangeStart).isEqualTo(expectedIds[1].toString())

    checkRightShardDoesNothing(run)

    assertThat(run.instanceProgressSnapshot[LEFT_SHARD]).isNotDone()

    run.singleScan()
    assertThat(run.batchesToRunSnapshot.single().utf8RangeStart()).isEqualTo(
        expectedIds[1].toString())
    assertThat(run.instanceProgressSnapshot[LEFT_SHARD].utf8PreviousEndKey()).isEqualTo(
        expectedIds[10].toString())

    run.execute()
    assertThat(run.backfill.idsRanDry).containsExactlyElementsOf(expectedIds.slice(1..19))
    assertThat(run.backfill.idsRanWet).isEmpty()
  }

  @Test fun withEndRange() {
    val expectedIds = createSomeOrders()
    // End after the 1st id, so only the first id should get backfilled.
    val run = backfila.createDryRun<TestBackfill>(rangeEnd = expectedIds[0].toString())
        .apply { configureForTest() }
    assertThat(run.rangeEnd).isEqualTo(expectedIds[0].toString())

    checkRightShardDoesNothing(run)

    assertThat(run.instanceProgressSnapshot[LEFT_SHARD]).isNotDone()

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
    val expectedIds = createSomeOrders()
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }
    run.precomputeRemaining()

    checkRightShardDoesNothing(run)

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
    assertThat(run.batchesToRunSnapshot.single().scannedRecordCount).isEqualTo(15) // Skipped some `beef`
    run.runBatch()
    assertThat(run).hasNoBatchesToRun()

    run.singleScan()
    assertThat(run).hasNoBatchesToRun().isFinishedScanning()

    assertThat(run).isComplete()
    assertThat(run.backfill.idsRanDry).containsExactlyElementsOf(expectedIds)
    assertThat(run.backfill.idsRanWet).isEmpty()
  }

  @Test fun testBackfillRun() {
    val expectedIds: List<Id<DbOrder>> = createSomeOrders()
    val run = backfila.createWetRun<TestBackfill>()
        .apply { configureForTest() }
    run.execute()
    assertThat(run.precomputeMatchingCount).isEqualTo(20)
    assertThat(run.backfill.idsRanDry).isEmpty()
    assertThat(run.backfill.idsRanWet).containsExactlyElementsOf(expectedIds)
  }

  @Test fun testParameter() {
    val expectedIds: List<Id<DbOrder>> = createSomeOrders()
    val run = backfila.createWetRun<TestBackfill>(parameters = mapOf("name" to "mcdonalds".encodeUtf8()))
        .apply { configureForTest() }
    run.execute()
    assertThat(run.precomputeMatchingCount).isEqualTo(5)
    assertThat(run.backfill.idsRanDry).isEmpty()
    assertThat(run.backfill.idsRanWet).doesNotContainAnyElementsOf(expectedIds)
  }

  private fun checkRightShardDoesNothing(run: BackfillRun<TestBackfill>) {
    // Right Shard will not match ot backfill anything since chickfila is on the left shard
    while (!run.instanceProgressSnapshot[RIGHT_SHARD].done) {
      val rightScan = run.shardScan(RIGHT_SHARD)
      if ( rightScan.batches.isNotEmpty()) {
        assertThat(rightScan.batches.single().matching_record_count).isEqualTo(0)
      }
      assertThat(run.backfill.idsRanDry).isEmpty()
      assertThat(run.backfill.idsRanWet).isEmpty()
    }
    assertThat(run.instanceProgressSnapshot[RIGHT_SHARD]).isDone()
  }

  private fun BackfillRun<*>.configureForTest() {
    this.batchSize = 10L
    this.scanSize = 100L
    this.computeCountLimit = 1L
  }

  private fun createSomeOrders(): List<Id<DbOrder>> {
    createRestaurants()
    return transacter.allowCowrites().transaction { session: Session ->
      val expected = mutableListOf<Id<DbOrder>>()
      repeat((1..10).count()) {
        val id = session.save(DbOrder(CHICKFILA))
        expected.add(id)
      }

      // Intersperse these to make sure we test skipping non matching records.
      repeat((1..5).count()) { session.save(DbOrder(MCDONALDS)) }
      repeat((1..2).count()) { session.save(DbOrder(WENDYS)) } // On the other shard

      repeat((1..10).count()) {
        val id = session.save(DbOrder(CHICKFILA))
        expected.add(id)
      }
      expected.toImmutableList()
    }
  }

  private fun createNoMatchingOrders() {
    createRestaurants()
    transacter.allowCowrites().transaction { session: Session ->
      repeat((1..5).count()) { session.save(DbOrder(MCDONALDS)) }
      repeat((1..2).count()) { session.save(DbOrder(WENDYS)) } // On the other shard
    }
  }

  private fun createRestaurants() {
    transacter.allowCowrites().transaction { session: Session ->
      val chickfila = session.save(DbRestaurant(CHICKFILA,"chickfila"))
      assertThat(chickfila).isEqualTo(CHICKFILA)
      assertThat(chickfila.shard(session)).isEqualTo(LEFT_SHARD)
      // Makes sure some non-matching make it on each shard, hash results should be a stable assumption.
      val mcdonalds = session.save(DbRestaurant(MCDONALDS, "mcdonalds"))
      assertThat(mcdonalds).isEqualTo(MCDONALDS)
      assertThat(mcdonalds.shard(session)).isEqualTo(LEFT_SHARD)
      val arbys = session.save(DbRestaurant(ARBYS, "arbys"))
      assertThat(arbys).isEqualTo(ARBYS)
      assertThat(arbys.shard(session)).isEqualTo(LEFT_SHARD)
      val wendys = session.save(DbRestaurant(WENDYS, "wendys"))
      assertThat(wendys).isEqualTo(WENDYS)
      assertThat(wendys.shard(session)).isEqualTo(RIGHT_SHARD)
    }
  }

  companion object {
    val KEYSPACE = DbRestaurant::class.java.getAnnotation(misk.hibernate.annotation.Keyspace::class.java).keyspace()
    // These are created by the vitess `vttestserver` on startup
    val LEFT_SHARD = Shard(KEYSPACE, "-80")
    val RIGHT_SHARD = Shard(KEYSPACE, "80-")

    // Created restaurants should have stable Ids as long as they are always created in order
    val CHICKFILA = Id<DbRestaurant>(1) // LEFT_SHARD
    val MCDONALDS = Id<DbRestaurant>(2) // LEFT_SHARD
    val ARBYS = Id<DbRestaurant>(3)     // LEFT_SHARD
    val WENDYS = Id<DbRestaurant>(4)    // RIGHT_SHARD
  }

  private fun Id<DbRestaurant>.shard(session: Session): Shard {
    val shards = session.shards(KEYSPACE).plus(Shard.SINGLE_SHARD)
    return shards.find { it.contains(this.shardKey()) }!!
  }

  private fun BackfillRun<TestBackfill>.shardScan(shard: Shard): GetNextBatchRangeResponse {
    return instanceScan(shard.name)
  }

  private operator fun Map<String, InstanceCursor>.get(shard: Shard) : InstanceCursor {
    return this[shard.name] ?: error("Snapshot missing instance shard ${shard}")
  }
}