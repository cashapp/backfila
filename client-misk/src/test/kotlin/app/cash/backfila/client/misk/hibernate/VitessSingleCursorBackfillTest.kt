package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbOrder
import app.cash.backfila.client.misk.DbRestaurant
import app.cash.backfila.client.misk.OrderQuery
import app.cash.backfila.client.misk.RestaurantQuery
import app.cash.backfila.client.misk.VitessSingleCursorInstanceProvider
import app.cash.backfila.client.misk.embedded.Backfila
import app.cash.backfila.client.misk.embedded.BackfillRun
import app.cash.backfila.client.misk.embedded.createDryRun
import app.cash.backfila.client.misk.embedded.createWetRun
import app.cash.backfila.client.misk.internal.InstanceCursor
import app.cash.backfila.client.misk.testing.assertThat
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import com.google.inject.Module
import javax.inject.Inject
import javax.persistence.criteria.JoinType
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root
import misk.hibernate.Check
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

@MiskTest(startService = true)
class VitessSingleCursorBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(true, listOf(TestBackfill::class))

  @Inject @ClientMiskService lateinit var transacter: Transacter
  @Inject lateinit var backfila: Backfila
  @Inject lateinit var queryFactory: Query.Factory

  @Test fun hibernateJoinThingy() {
    val query = """
       select count(distinct orders.id)
       from orders
       join restaurants on orders.restaurant_id = restaurants.id
       where 
         restaurants.name='backfila' and 
         orders.id>=1 and 
         orders.id<=5
       limit 1"""

    createSomeOrders()

    transacter.transaction { session ->

      val criteriaBuilder = session.hibernateSession.criteriaBuilder
      val query = criteriaBuilder.createQuery(Any::class.java)

      val orderRoot: Root<DbOrder> = query.from(DbOrder::class.java)
      val joinRestaurant = orderRoot.join<Id<DbRestaurant>, DbRestaurant>("restaurant", JoinType.INNER)
      joinRestaurant.on(criteriaBuilder.equal(joinRestaurant.get<Any>("id"), orderRoot.get<Any>("restaurant_id")))

      val predicate = criteriaBuilder.and(
          criteriaBuilder.equal(joinRestaurant.get<Any>("name"), "chickfila"),
          criteriaBuilder.lessThanOrEqualTo(orderRoot.get<Any>("id") as Path<Comparable<Comparable<*>>>, Id<DbOrder>(5L) as Comparable<Comparable<*>>),
          criteriaBuilder.greaterThanOrEqualTo(orderRoot.get<Any>("id") as Path<Comparable<Comparable<*>>>, Id<DbOrder>(1L) as Comparable<Comparable<*>>)
      )

      query.where(predicate)

      query.select(criteriaBuilder.count(orderRoot))

      val typedQuery = session.hibernateSession.createQuery(query)
      val result = session.disableChecks(listOf(Check.FULL_SCATTER)) { typedQuery.singleResult }

      println("Found stuff $result") //TODO:HERE

//      query.where(criteriaBuilder.equal(restaurantRoot.get<Any>("id"), orderRoot.get<Any>("restaurant_id")))
//
//      val orderEntity = session.hibernateSession.entityManagerFactory.metamodel.entity(DbOrder::class.java)
//      val restaurantEntity = session.hibernateSession.entityManagerFactory.metamodel.entity(DbRestaurant::class.java)
//
//
// //      val orderRestaurantId = orderEntity.getAttribute("restaurant_id") as SingularAttribute<in DbOrder, Id<DbRestaurant>>
//      val restaurantId = restaurantEntity.getAttribute("id") as SingularAttribute<in DbRestaurant, Id<DbRestaurant>>
//
//
//      //orderRoot.join("restaurant_id", DbRestarant::class, "id", JoinType.INNER)
//

      val count = queryFactory.newQuery(OrderQuery::class)
          .idGe(Id(1L))
          .idLe(Id(5L))
          .apply {
            dynamicJoin(RestaurantQuery::class, "restaurant", JoinType.INNER) {
              name("chickfila")
            }
          }
          .count(session)

      println(count)
    }
  }

  private class TestBackfill @Inject constructor(
    @ClientMiskService private val transacter: Transacter,
    private val queryFactory: Query.Factory
  ) : Backfill<DbOrder, Id<DbOrder>>() {
    val idsRanDry = mutableListOf<Id<DbOrder>>()
    val idsRanWet = mutableListOf<Id<DbOrder>>()

    override fun backfillCriteria(config: BackfillConfig): Query<DbOrder> {
      val name = (config.parameters["name"] ?: "chickfila".encodeUtf8()).utf8()
      if (name.isEmpty()) {
        return queryFactory.newQuery(OrderQuery::class)
      }
      return queryFactory.newQuery(OrderQuery::class).restaurantName(name)
    }

    override fun validate(config: BackfillConfig) {
      if ("badparameter".encodeUtf8().equals(config.parameters["name"])) {
        error("bad parameter value")
      }
    }

    override fun runBatch(pkeys: List<Id<DbOrder>>, config: BackfillConfig) {
      if (config.dryRun) {
        idsRanDry.addAll(pkeys)
      } else {
        idsRanWet.addAll(pkeys)
      }
    }

    override fun instanceProvider() = VitessSingleCursorInstanceProvider(transacter, this)
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
    createNoMatchingOrders()
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
    val expectedIds = createSomeOrders()
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
    val expectedIds = createSomeOrders()
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
    val expectedIds = createSomeOrders()
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
    val expectedIds = createSomeOrders()
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
    createSomeOrders()
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
    createSomeOrders()
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
    createSomeOrders()
    val run = backfila.createDryRun<TestBackfill>()
        .apply { configureForTest() }

    // Requested 20 batches but only 2 batches in the table.
    run.computeCountLimit = 20L
    run.singleScan()
    assertThat(run.batchesToRunSnapshot).hasSize(2)
    assertThat(run.batchesToRunSnapshot).allMatch { it.matchingRecordCount == 10L }
  }

  @Test fun wetAndDryRunProcessSameElements() {
    createSomeOrders()
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
      val chickfila = session.save(DbRestaurant(CHICKFILA, "chickfila"))
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
    val KEYSPACE =
        DbRestaurant::class.java.getAnnotation(misk.hibernate.annotation.Keyspace::class.java)
            .keyspace()
    // These are created by the vitess `vttestserver` on startup
    val LEFT_SHARD = Shard(KEYSPACE, "-80")
    val RIGHT_SHARD = Shard(KEYSPACE, "80-")

    // Created restaurants should have stable Ids as long as they are always created in order
    val CHICKFILA = Id<DbRestaurant>(1) // LEFT_SHARD
    val MCDONALDS = Id<DbRestaurant>(2) // LEFT_SHARD
    val ARBYS = Id<DbRestaurant>(3) // LEFT_SHARD
    val WENDYS = Id<DbRestaurant>(4) // RIGHT_SHARD
  }

  private fun Id<DbRestaurant>.shard(session: Session): Shard {
    val shards = session.shards(KEYSPACE).plus(Shard.SINGLE_SHARD)
    return shards.find { it.contains(this.shardKey()) }!!
  }

  private fun BackfillRun<TestBackfill>.shardScan(shard: Shard): GetNextBatchRangeResponse {
    return instanceScan(shard.name)
  }

  private operator fun Map<String, InstanceCursor>.get(shard: Shard): InstanceCursor {
    return this[shard.name] ?: error("Snapshot missing instance shard $shard")
  }
}
