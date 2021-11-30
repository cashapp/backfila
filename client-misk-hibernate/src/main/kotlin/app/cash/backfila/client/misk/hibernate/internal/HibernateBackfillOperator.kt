package app.cash.backfila.client.misk.hibernate.internal

import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.internal.BackfillOperatorFactory
import app.cash.backfila.client.misk.hibernate.HibernateBackfill
import app.cash.backfila.client.misk.hibernate.PrimaryKeyCursorMapper
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillOperator
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
import java.util.concurrent.TimeUnit
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root
import misk.exceptions.BadRequestException
import misk.hibernate.DbEntity
import misk.hibernate.Operator.GE
import misk.hibernate.Operator.GT
import misk.hibernate.Operator.LE
import misk.hibernate.Query
import wisp.logging.getLogger

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
  private var primaryKeyCursorMapper: PrimaryKeyCursorMapper = backend.primaryKeyCursorMapper
  internal var queryFactory: Query.Factory = backend.queryFactory

  override fun name() = backfill.javaClass.toString()

  private fun validateRange(range: KeyRange?) {
    if (range == null) {
      return
    }

    if (range.start != null) {
      primaryKeyCursorMapper.fromByteString(backfill.pkeyClass.java, range.start).onFailure { ex ->
        throw BadRequestException("Start of requested range is invalid", ex)
      }
    }

    if (range.end != null) {
      primaryKeyCursorMapper.fromByteString(backfill.pkeyClass.java, range.end).onFailure { ex ->
        throw BadRequestException("End of requested range is invalid", ex)
      }
    }
  }

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    validateRange(request.range)

    backfill.validate(
      parametersOperator.constructBackfillConfig(
        request.parameters, request.dry_run
      )
    )

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
            criteriaBuilder.max(backfill.getPrimaryKeyPath(queryRoot))
          )
        }!!

      val min = minmax[0] as Pkey?
      val max = minmax[1] as Pkey?
      if (min == null) {
        // Empty table, no work to do for this partition.
        KeyRange.Builder().build()
      } else {
        checkNotNull(max) { "Table max was null but min wasn't, this shouldn't happen" }
        KeyRange.Builder()
          .start(requestedRange?.start ?: primaryKeyCursorMapper.toByteString(min))
          .end(requestedRange?.end ?: primaryKeyCursorMapper.toByteString(max))
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
          TimeUnit.MILLISECONDS
        ) > request.compute_time_limit_ms
      ) {
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
      request.parameters, request.dry_run
    )
    private val precomputing: Boolean = request.precomputing == true

    // Initialized from the request and gets updated as batches are returned.
    private var previousEndKey: Pkey? = request.previous_end_key?.let {
      primaryKeyCursorMapper.fromByteString(backfill.pkeyClass.java, it).getOrThrow()
    }
    private var boundingMax: Pkey? = null

    private fun addBoundingMin(query: Query<E>) {
      if (previousEndKey != null) {
        query.dynamicAddConstraint(backfill.primaryKeyHibernateName(), GT, previousEndKey)
      } else {
        val startPkey = primaryKeyCursorMapper.fromByteString(
          backfill.pkeyClass.java,
          backfillRange.start!!,
        ).getOrThrow()
        query.dynamicAddConstraint(backfill.primaryKeyHibernateName(), GE, startPkey)
      }
    }

    operator fun next(): Batch? {
      // Scan a big chunk of records to have a reasonable bound for the next query.
      // We find all matching batches in each scan bound to avoid repeating this work.
      if (boundingMax == null) {
        val stopwatch = Stopwatch.createStarted()
        boundingMax = boundingRangeStrategy
          .computeBoundingRangeMax(
            backfill,
            partitionName,
            previousEndKey,
            primaryKeyCursorMapper.fromByteString(backfill.pkeyClass.java, backfillRange.start).getOrThrow(),
            primaryKeyCursorMapper.fromByteString(backfill.pkeyClass.java, backfillRange.end).getOrThrow(),
            scanSize,
          )
        if (boundingMax == null) {
          logger.info("Bounding range returned no records, done computing batches")
          return null
        }
        logger.info(
          "Computed scan bound for next batch: [$previousEndKey, $boundingMax]. " +
            "Took $stopwatch"
        )
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
          batchEndPkey = stringBatchEndPkeyRow?.single() as Pkey?
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
            criteriaBuilder.count(queryRoot)
          )
        }!!
        @Suppress("UNCHECKED_CAST") // Return type from the query should always match.
        val start = result[0] as Pkey
        val scannedCount = result[1] as Long

        TxResult(
          end,
          Batch.Builder()
            .batch_range(
              KeyRange.Builder()
                .start(primaryKeyCursorMapper.toByteString(start))
                .end(primaryKeyCursorMapper.toByteString(end))
                .build()
            )
            .scanned_record_count(scannedCount)
            .matching_record_count(matchingCount ?: 0)
            .build()
        )
      }
      if (txResult.end == boundingMax) {
        // Reached the end of this bounding range, null it out so a new one is computed when more
        // batches are requested.
        boundingMax = null
      }
      previousEndKey = txResult.end
      return txResult.batch
    }
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    val config = parametersOperator.constructBackfillConfig(
      request.parameters, request.dry_run
    )

    val pkeys = partitionProvider.transaction(request.partition_name) { session ->
      val min = primaryKeyCursorMapper.fromByteString(backfill.pkeyClass.java, request.batch_range.start).getOrThrow()
      val max = primaryKeyCursorMapper.fromByteString(backfill.pkeyClass.java, request.batch_range.end).getOrThrow()
      val pkeyProperty = backfill.primaryKeyHibernateName()
      val idList = backfill.backfillCriteria(config).apply {
        dynamicAddConstraint(pkeyProperty, GE, min)
        dynamicAddConstraint(pkeyProperty, LE, max)
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
