package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.ForBackfila
import app.cash.backfila.client.misk.PkeySqlAdapter
import app.cash.backfila.client.misk.internal.BackfillOperator.Factory
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse.Batch
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse.Instance
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.base.Preconditions.checkArgument
import com.google.common.base.Stopwatch
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.collect.ImmutableList
import com.google.inject.Injector
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import misk.exceptions.BadRequestException
import misk.hibernate.DbEntity
import misk.hibernate.Id
import misk.hibernate.Operator.GE
import misk.hibernate.Operator.GT
import misk.hibernate.Operator.LE
import misk.hibernate.Query
import misk.logging.getLogger
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * Operates on a backfill using Hibernate 5.x entities. Create instances with [Factory].
 *
 * @param <E> Entity class being backfilled. Determines the table that is iterated.
 * @param <Pkey> The type of the primary key for the backfill, i.e. the value being iterated on.
 * Usually an Id<E>.
 */
internal class BackfillOperator<E : DbEntity<E>, Pkey : Any> internal constructor(
  val backfill: Backfill<E, Pkey>,
  factory: Factory
) {
  private val instanceProvider = backfill.instanceProvider()
  private val boundingRangeStrategy = instanceProvider.boundingRangeStrategy<E, Pkey>()
  private var pkeySqlAdapter: PkeySqlAdapter = factory.pkeySqlAdapter
  internal var queryFactory: Query.Factory = factory.queryFactory

  fun name() = backfill.javaClass.toString()

  private fun pkeyFromString(string: String): Pkey =
      pkeySqlAdapter.pkeyFromString(backfill.pkeyClass.java, string)

  // TODO this should be handled by a subtype that is tailored to integer primary keys
  private fun validateRange(range: KeyRange?) {
    if (range == null) return
    try {
      if (range.start != null) {
        range.start.utf8().toLong()
      }
    } catch (e: NumberFormatException) {
      throw BadRequestException("Start of range must be a number", e)
    }

    try {
      if (range.end != null) {
        range.end.utf8().toLong()
      }
    } catch (e: NumberFormatException) {
      throw BadRequestException("End of range must be a number", e)
    }
  }

  fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    validateRange(request.range)

    backfill.validate(BackfillConfig(request.parameters, request.dry_run))

    return PrepareBackfillResponse.Builder()
        .instances(instanceProvider.names(request).map { instanceForShard(it, request.range) })
        .build()
  }

  private fun instanceForShard(
    instanceName: String,
    requestedRange: KeyRange?
  ): Instance {
    if (requestedRange?.start != null && requestedRange.end != null) {
      return Instance.Builder()
          .instance_name(instanceName)
          .backfill_range(requestedRange)
          .estimated_record_count(null)
          .build()
    }
    val keyRange: KeyRange = instanceProvider.transaction(instanceName) { session ->
      val minmax = queryFactory.dynamicQuery(backfill.entityClass)
          .dynamicUniqueResult(session) { criteriaBuilder, queryRoot ->
            criteriaBuilder.tuple(
                criteriaBuilder.min(queryRoot.get(backfill.primaryKeyHibernateName())),
                criteriaBuilder.max(queryRoot.get(backfill.primaryKeyHibernateName())))
          }!!

      val min = minmax[0]
      val max = minmax[1]
      if (min == null) {
        // Empty table, no work to do for this instance.
        KeyRange.Builder().build()
      } else {
        KeyRange.Builder()
            .start(requestedRange?.start ?: min.toString().encodeUtf8())
            .end(requestedRange?.end ?: max.toString().encodeUtf8())
            .build()
      }
    }
    return Instance.Builder()
        .instance_name(instanceName)
        .backfill_range(keyRange)
        .estimated_record_count(null)
        .build()
  }

  fun getNextBatchRange(
    request: GetNextBatchRangeRequest
  ): GetNextBatchRangeResponse {
    checkArgument(request.compute_count_limit > 0, "batch limit must be > 0")
    if (request.backfill_range.start == null) {
      // This instance never had any data, stop it immediately.
      return GetNextBatchRangeResponse.Builder()
          .batches(ImmutableList.of<Batch>())
          .build()
    }

    val stopwatch = Stopwatch.createStarted()
    val batchGenerator = BatchGenerator(request)

    val batches = ArrayList<Batch>()
    while (batches.size < request.compute_count_limit) {
      val batch = batchGenerator.next()
          ?: break // No more batches can be computed.

      batches.add(batch)

      if (request.compute_time_limit_ms != null && stopwatch.elapsed(
              TimeUnit.MILLISECONDS) > request.compute_time_limit_ms) {
        break
      }
    }

    return GetNextBatchRangeResponse.Builder()
        .batches(batches)
        .build()
  }

  private inner class BatchGenerator internal constructor(request: GetNextBatchRangeRequest) {
    private val instanceName: String = request.instance_name
    private val batchSize: Long = request.batch_size
    private val scanSize: Long = request.scan_size
    private val backfillRange: KeyRange = request.backfill_range
    private val config: BackfillConfig = BackfillConfig(request.parameters, request.dry_run)
    private val precomputing: Boolean = request.precomputing == true

    // Initialized from the request and gets updated as batches are returned.
    private var previousEndKey: ByteString? = request.previous_end_key

    private var boundingMax: Pkey? = null

    private fun addBoundingMin(query: Query<E>) {
      if (previousEndKey != null) {
        val previousEndPkey = pkeyFromString(previousEndKey!!.utf8())
        query.dynamicAddConstraint(backfill.primaryKeyHibernateName(), GT, previousEndPkey)
      } else {
        val startPkey = pkeyFromString(backfillRange.start.utf8())
        query.dynamicAddConstraint(backfill.primaryKeyHibernateName(), GE, startPkey)
      }
    }

    operator fun next(): Batch? {
      // Scan a big chunk of records to have a reasonable bound for the next query.
      // We find all matching batches in each scan bound to avoid repeating this work.
      if (boundingMax == null) {
        val stopwatch = Stopwatch.createStarted()
        boundingMax = boundingRangeStrategy
            .computeBoundingRangeMax(backfill, instanceName, previousEndKey, backfillRange, scanSize)
        if (boundingMax == null) {
          logger.info("Bounding range returned no records, done computing batches")
          return null
        }
        logger.info("Computed scan bound for next batch: [$previousEndKey, $boundingMax]. " +
            "Took $stopwatch")
      }

      class TxResult(val end: Pkey, val batch: Batch)

      val pkeyProperty = backfill.primaryKeyHibernateName()
      val txResult = instanceProvider.transaction(instanceName) { session ->
        val batchEndPkey: Pkey?
        if (precomputing) {
          // No need to find correct-sized batches, just quickly compute a count.
          batchEndPkey = null
        } else {
          // Now that we have a bound, this query can find criteria-matching batches without
          // becoming a long running query.
          // Hibernate doesn't support subqueries in FROM, but we can use a limit+offset
          // to figure out the last id in the batch.
          // We can't use raw SQL as above because we're working with a backfill-provided Criteria.
          val stringBatchEndPkeyRow = backfill.backfillCriteria(config).apply {
            addBoundingMin(this)
            dynamicAddConstraint(pkeyProperty, LE, boundingMax)
            dynamicAddOrder(pkeyProperty, asc = true)
            firstResult = (batchSize - 1).toInt()
            maxRows = 1
          }.dynamicUniqueResult(session, listOf(pkeyProperty))

          @Suppress("UNCHECKED_CAST") // Return type from the query should always match.
          batchEndPkey =
              stringBatchEndPkeyRow?.single() as Pkey? // I think we are always getting a Pkey here so the cast should be safe.
        }

        val matchingCount: Long?
        val end: Pkey
        if (batchEndPkey == null) {
          // Less than batchSize matches, so return the end of the scan size and count the matches.
          val result = backfill.backfillCriteria(config).apply {
            addBoundingMin(this)
            dynamicAddConstraint(pkeyProperty, LE, boundingMax)
          }.dynamicUniqueResult(session) { criteriaBuilder, queryRoot ->
            criteriaBuilder.countDistinct(queryRoot.get<E>(pkeyProperty))
          }!!
          matchingCount = result[0] as Long?
          end = boundingMax!!
        } else {
          // We got an id, so there's exactly batchSize results.
          matchingCount = batchSize
          end = batchEndPkey
        }

        // Get start pkey and scanned record count for this batch.
        val result = queryFactory.dynamicQuery(backfill.entityClass).apply {
          addBoundingMin(this)
          dynamicAddConstraint(pkeyProperty, LE, end)
        }.dynamicUniqueResult(session) { criteriaBuilder, queryRoot ->
          criteriaBuilder.tuple(
              criteriaBuilder.min(queryRoot.get(pkeyProperty)),
              criteriaBuilder.count(queryRoot))
        }!!
        val start = result[0].toString()
        val scannedCount = result[1] as Long

        TxResult(end,
            Batch.Builder()
                .batch_range(KeyRange.Builder()
                    .start(start.encodeUtf8())
                    .end(end.toString().encodeUtf8())
                    .build())
                .scanned_record_count(scannedCount)
                .matching_record_count(matchingCount ?: 0)
                .build())
      }
      if (txResult.end == boundingMax) {
        // Reached the end of this bounding range, null it out so a new one is computed when more
        // batches are requested.
        boundingMax = null
      }
      previousEndKey = txResult.batch.batch_range.end
      return txResult.batch
    }
  }

  fun runBatch(request: RunBatchRequest): RunBatchResponse {
    val config = BackfillConfig(request.parameters,
        request.dry_run)

    val pkeys = instanceProvider.transaction(request.instance_name) { session ->
      val minId = Id<E>(request.batch_range.start.utf8().toLong())
      val maxId = Id<E>(request.batch_range.end.utf8().toLong())
      val pkeyProperty = backfill.primaryKeyHibernateName()
      val idList = backfill.backfillCriteria(config).apply {
        dynamicAddConstraint(pkeyProperty, GE, minId)
        dynamicAddConstraint(pkeyProperty, LE, maxId)
        dynamicAddOrder(pkeyProperty, true)
      }.dynamicList(session, listOf(pkeyProperty))

      // Convert them back to a list of Pkey
      @Suppress("UNCHECKED_CAST") // Return type from the query should always match.
      idList.map { it.single() } as List<Pkey>
    }
    backfill.runBatch(pkeys, config)

    return RunBatchResponse.Builder().build()
  }

  @Singleton
  class Factory @Inject constructor(
    private val injector: Injector,
    @ForBackfila private val backfills: Map<String, KClass<out Backfill<*, *>>>,
    internal var pkeySqlAdapter: PkeySqlAdapter,
    internal var queryFactory: Query.Factory
  ) {
    /**
     * Caches backfill instances by backfill id, which comes from backfila. We might have multiple
     * instances of the same backfill class, but the config is immutable per id so it is safe to
     * cache even if the backfill stores some state.
     */
    private val instanceCache: Cache<String, Backfill<*, *>> = CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build()

    /** Creates Backfill instances. Each backfill ID gets a new Backfill instance. */
    private fun getBackfill(name: String, backfillId: String): Backfill<*, *> {
      return instanceCache.get(backfillId) {
        val backfillClass = backfills[name]
        if (backfillClass == null) {
          logger.warn("Unknown backfill %s, was it deleted while running?", name)
          throw BadRequestException("Unknown backfill $name")
        }
        injector.getInstance(backfillClass.java)
      }
    }

    fun <E : DbEntity<E>, Pkey : Any> create(backfill: Backfill<E, Pkey>) =
        BackfillOperator(backfill, this)

    fun create(backfillName: String, backfillId: String): BackfillOperator<*, *> {
      @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
      val backfill = getBackfill(backfillName, backfillId)
          as Backfill<DbPlaceholder, Any>

      return create(backfill)
    }

    /** This placeholder exists so we can create a backfill without a type parameter. */
    private class DbPlaceholder : DbEntity<DbPlaceholder> {
      override val id: Id<DbPlaceholder> get() = throw IllegalStateException("unreachable")
    }
  }

  companion object {
    private val logger = getLogger<BackfillOperator<*, *>>()
  }
}
