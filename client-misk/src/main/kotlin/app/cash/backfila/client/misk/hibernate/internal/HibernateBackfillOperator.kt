package app.cash.backfila.client.misk.hibernate.internal

import app.cash.backfila.client.misk.NoParameters
import app.cash.backfila.client.misk.hibernate.HibernateBackfill
import app.cash.backfila.client.misk.hibernate.PkeySqlAdapter
import app.cash.backfila.client.misk.internal.BackfilaParametersOperator
import app.cash.backfila.client.misk.spi.BackfillOperator
import app.cash.backfila.client.misk.internal.BackfillOperatorFactory
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse.Batch
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse.Partition
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.base.Preconditions.checkArgument
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root
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
 * Operates on a backfill using Hibernate 5.x entities. Create instances with [BackfillOperatorFactory].
 *
 * @param <E> Entity class being backfilled. Determines the table that is iterated.
 * @param <Pkey> The type of the primary key for the backfill, i.e. the value being iterated on.
 * Usually an Id<E>.
 * @param <Param> A class wrapping the parameters that come from backfila. The default constructor
 * is used to specify the parameters and construct the class. Usually a data class or [NoParameters].
 */
internal class HibernateBackfillOperator<E : DbEntity<E>, Pkey : Any, Param : Any> internal constructor(
  override val backfill: HibernateBackfill<E, Pkey, Param>,
  private val parametersOperator: BackfilaParametersOperator<Param>,
  backend: HibernateBackend
) : BackfillOperator {
  private val partitionProvider = backfill.partitionProvider()
  private val boundingRangeStrategy = partitionProvider.boundingRangeStrategy<E, Pkey>()
  private var pkeySqlAdapter: PkeySqlAdapter = backend.pkeySqlAdapter
  internal var queryFactory: Query.Factory = backend.queryFactory

  override fun name() = backfill.javaClass.toString()

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

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    validateRange(request.range)

    backfill.validate(parametersOperator.constructBackfillConfig(
        request.parameters, request.dry_run))

    return PrepareBackfillResponse.Builder()
        .partitions(partitionProvider.names(request).map { partitionForShard(it, request.range) })
        .build()
  }

  private fun partitionForShard(
    partitionName: String,
    requestedRange: KeyRange?
  ): Partition {
    if (requestedRange?.start != null && requestedRange.end != null) {
      return Partition.Builder()
          .partition_name(partitionName)
          .backfill_range(requestedRange)
          .estimated_record_count(null)
          .build()
    }
    val keyRange: KeyRange = partitionProvider.transaction(partitionName) { session ->
      val minmax = queryFactory.dynamicQuery(backfill.entityClass)
          .dynamicUniqueResult(session) { criteriaBuilder, queryRoot ->
            criteriaBuilder.tuple(
                criteriaBuilder.min(backfill.getPrimaryKeyPath(queryRoot)),
                criteriaBuilder.max(backfill.getPrimaryKeyPath(queryRoot)))
          }!!

      val min = minmax[0]
      val max = minmax[1]
      if (min == null) {
        // Empty table, no work to do for this partition.
        KeyRange.Builder().build()
      } else {
        KeyRange.Builder()
            .start(requestedRange?.start ?: min.toString().encodeUtf8())
            .end(requestedRange?.end ?: max.toString().encodeUtf8())
            .build()
      }
    }
    return Partition.Builder()
        .partition_name(partitionName)
        .backfill_range(keyRange)
        .estimated_record_count(null)
        .build()
  }

  override fun getNextBatchRange(
    request: GetNextBatchRangeRequest
  ): GetNextBatchRangeResponse {
    checkArgument(request.compute_count_limit > 0, "batch limit must be > 0")
    if (request.backfill_range.start == null) {
      // This partition never had any data, stop it immediately.
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
    private val partitionName: String = request.partition_name
    private val batchSize: Long = request.batch_size
    private val scanSize: Long = request.scan_size
    private val backfillRange: KeyRange = request.backfill_range
    private val config = parametersOperator.constructBackfillConfig(
        request.parameters, request.dry_run)
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
            .computeBoundingRangeMax(backfill, partitionName, previousEndKey, backfillRange,
                scanSize)
        if (boundingMax == null) {
          logger.info("Bounding range returned no records, done computing batches")
          return null
        }
        logger.info("Computed scan bound for next batch: [$previousEndKey, $boundingMax]. " +
            "Took $stopwatch")
      }

      class TxResult(val end: Pkey, val batch: Batch)

      val pkeyProperty = backfill.primaryKeyHibernateName()
      val txResult = partitionProvider.transaction(partitionName) { session ->
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
            criteriaBuilder.countDistinct(backfill.getPrimaryKeyPath(queryRoot))
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
              criteriaBuilder.min(backfill.getPrimaryKeyPath(queryRoot)),
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

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    val config = parametersOperator.constructBackfillConfig(
        request.parameters, request.dry_run)

    val pkeys = partitionProvider.transaction(request.partition_name) { session ->
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

  private fun HibernateBackfill<*, *, *>.getPrimaryKeyPath(queryRoot: Root<*>): Path<Number> {
    val fields = primaryKeyHibernateName().split('.')
    var path = queryRoot as Path<Number>
    for (field in fields) {
      path = path.get(field)
    }
    return path
  }

  companion object {
    private val logger = getLogger<HibernateBackfillOperator<*, *, *>>()
  }
}
