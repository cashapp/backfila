package app.cash.backfila.client.misk.base

import app.cash.backfila.client.misk.Backfill
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
import com.google.common.collect.ImmutableList
import com.google.inject.TypeLiteral
import misk.exceptions.BadRequestException
import misk.hibernate.DbEntity
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Session
import misk.logging.getLogger
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.hibernate.criterion.Criterion
import org.hibernate.criterion.Order
import org.hibernate.criterion.Projections
import org.hibernate.criterion.Restrictions
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.persistence.Table

/**
 * Generic backfill that runs over Hibernate 4 entities.
 *
 * @param <E> Entity class being backfilled. Determines the table that is iterated.
 * @param <Pkey> The type of the primary key for the backfill, i.e. the value being iterated on.
 * Usually an Id<E>.
 */
abstract class HibernateBackfill<E : DbEntity<E>, Pkey> @Inject constructor(
  internal var entityType: TypeLiteral<E>,
  internal var pkeyType: TypeLiteral<Pkey>,
  internal var pkeySqlAdapter: PkeySqlAdapter
) : Backfill {

  /**
   * The name of the column that the backfill is keyed off of. Usually the primary key of the table.
   * Column must be unique and define an ordering.
   */
  protected fun primaryKeyName(): String = "id"

  /**
   * The name of the hibernate property that the backfill is keyed off of.
   * Separate from primaryKeyName() as the casing is usually different.
   */
  protected fun primaryKeyHibernateName(): String = "id"

  /**
   * Criteria that filters which records are selected to backfill from the table.
   *
   * This must return a new instance of DetachedCriteria in every invocation.
   */
  protected abstract fun backfillCriteria(config: BackfillConfig): Query<E>

  /**
   * Override this and throw an exception to prevent the backfill from being created.
   * This is also a good place to do any prep work before batches are run.
   */
  protected fun validate(config: BackfillConfig) {}

  /**
   * Called for each batch of matching records.
   * Override in a backfill to process all records in a batch.
   */
  protected fun runBatch(pkeys: List<Pkey>, config: BackfillConfig) {
    for (pkey in pkeys) {
      runOne(pkey, config)
    }
  }

  /**
   * Called for each matching record.
   * Override in a backfill to process one record at a time.
   */
  protected fun runOne(pkey: Pkey, config: BackfillConfig) {

  }

  // TODO probably move these to a ShardsProvider interface
  protected abstract fun <T> transactionForInstance(
    instanceName: String,
    task: (Session) -> T
  ): T

  protected abstract fun instanceNames(request: PrepareBackfillRequest): List<String>

  protected fun entityClass(): Class<E> {
    @Suppress("UNCHECKED_CAST") // The E type parameter is runtime-checked.
    return entityType.rawType as Class<E>
  }

  protected fun pkeyClass(): Class<Pkey> {
    @Suppress("UNCHECKED_CAST") // The Pkey type parameter is runtime-checked.
    return pkeyType.rawType as Class<Pkey>
  }

  private fun pkeyFromString(string: String): Pkey {
    @Suppress("UNCHECKED_CAST") // The Pkey type parameter is runtime-checked.
    return pkeySqlAdapter.fromString(pkeyClass(), string) as Pkey
  }

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

    validate(BackfillConfig(request.parameters, request.dry_run))

    return PrepareBackfillResponse.Builder()
        .instances(instanceNames(request).map { instanceForShard(it, request.range) })
        .build()
  }

  private fun instanceForShard(
    instanceName: String,
    requestedRange: KeyRange?
  ): Instance {
    if (requestedRange != null && requestedRange.start != null && requestedRange.end != null) {
      return Instance.Builder()
          .instance_name(instanceName)
          .backfill_range(requestedRange)
          .estimated_record_count(null)
          .build()
    }
    val keyRange: KeyRange = transactionForInstance<KeyRange>(instanceName, { session ->
      val minmax: Array<Any?> = session.hibernateSession.createCriteria(entityClass())
          .setProjection(Projections.projectionList()
              .add(Projections.min(primaryKeyHibernateName()))
              .add(Projections.max(primaryKeyHibernateName())))
          .uniqueResult() as Array<Any?>

      val min = minmax[0]
      val max = minmax[1]
      if (min == null) {
        // Empty table, no work to do for this instance.
        return@transactionForInstance KeyRange.Builder().build()
      }

      return@transactionForInstance KeyRange.Builder()
          .start(requestedRange?.start ?: min.toString().encodeUtf8())
          .end(requestedRange?.end ?: max.toString().encodeUtf8())
          .build()
    })
    return Instance.Builder()
        .instance_name(instanceName)
        .backfill_range(keyRange)
        .estimated_record_count(null)
        .build()
  }

  /**
   * Computes a bound of size request.scan_size, to get a set of records that can be scanned for
   * records that match the criteria.
   * Returns null if there is are no more records left in the table.
   */
  protected fun computeBoundingRangeMax(
    instanceName: String,
    previousEndKey: ByteString?, backfillRange: KeyRange, scanSize: Long?
  ): Pkey? {
    return transactionForInstance(instanceName, { session ->
      selectMaxBound(session, schemaAndTable(), previousEndKey, backfillRange, scanSize)
    })
  }

  override fun getNextBatchRange(
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
          ?: // No more batches can be computed.
          break

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

  protected fun selectMaxBound(
    session: Session,
    schemaAndTable: String, previousEndKey: ByteString?,
    backfillRange: KeyRange, scanSize: Long?
  ): Pkey? {
    // Hibernate doesn't support subqueries in FROM, and we don't want to read in 100k+ records,
    // so we use raw SQL here.
    val pkeyName = primaryKeyName()
    var where = when {
      previousEndKey != null -> "WHERE $pkeyName > ${previousEndKey.utf8()}"
      else -> "WHERE $pkeyName >= ${backfillRange.start.utf8()}"
    }
    where += " AND $pkeyName <= ${backfillRange.end.utf8()}"
    val sql = """
        |SELECT MAX(s.${pkeyName}) FROM
        | (SELECT DISTINCT ${pkeyName} FROM ${schemaAndTable}
        | ${where}
        | ORDER BY ${pkeyName}
        | LIMIT ${scanSize}) s
        """.trimMargin()
    val max = session.hibernateSession.createSQLQuery(sql).uniqueResult();
    return pkeyFromString(max.toString())
  }

  private inner class BatchGenerator internal constructor(request: GetNextBatchRangeRequest) {
    private val instanceName: String
    private val batchSize: Long
    private val scanSize: Long
    private val backfillRange: KeyRange
    private val config: BackfillConfig
    private val precomputing: Boolean

    // Initialized from the request and gets updated as batches are returned.
    private var previousEndKey: ByteString? = null

    private var boundingMax: Pkey? = null

    init {
      instanceName = request.instance_name
      batchSize = request.batch_size
      scanSize = request.scan_size
      backfillRange = request.backfill_range
      previousEndKey = request.previous_end_key
      config = BackfillConfig(request.parameters, request.dry_run)
      precomputing = java.lang.Boolean.TRUE == request.precomputing
    }

    private fun boundingMin(): Criterion {
      if (previousEndKey != null) {
        val previousEndPkey = pkeyFromString(previousEndKey!!.utf8())
        return Restrictions.gt(primaryKeyHibernateName(), previousEndPkey)
      }
      val startPkey = pkeyFromString(backfillRange.start.utf8())
      return Restrictions.ge(primaryKeyHibernateName(), startPkey)
    }

    operator fun next(): Batch? {
      // Scan a big chunk of records to have a reasonable bound for the next query.
      // We find all matching batches in each scan bound to avoid repeating this work.
      if (boundingMax == null) {
        val stopwatch = Stopwatch.createStarted()
        boundingMax = computeBoundingRangeMax(instanceName, previousEndKey, backfillRange, scanSize)
        if (boundingMax == null) {
          logger.info("Bounding range returned no records, done computing batches")
          return null
        }
        logger.info("Computed scan bound for next batch: [$previousEndKey, $boundingMax]. " +
            "Took $stopwatch")
      }

      class TxResult(val end: Pkey, val batch: Batch)

      val pkeyProperty = primaryKeyHibernateName()
      val txResult = transactionForInstance<TxResult>(instanceName) { session ->
        val boundingMin = boundingMin()

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
          batchEndPkey = backfillCriteria(config)
//              .add(boundingMin)
//              .add(Restrictions.le(pkeyProperty, boundingMax))
//              .addOrder(Order.asc(pkeyProperty))
//              .setFirstResult((batchSize - 1) as Int)
//              .setMaxResults(1)
            Projections.distinct(Projections.property(pkeyProperty))
              .uniqueResult(session) as Pkey?;
        }

        val matchingCount: Long?
        val end: Pkey
        if (batchEndPkey == null) {
          // Less than batchSize matches, so return the end of the scan size and count the matches.
          //                    matchingCount = session.getExecutableCriteria(backfillCriteria(config))
          //                            .<Long>setProjection(Projections.countDistinct(pkeyProperty))
          //                            .add(boundingMin)
          //                            .add(Restrictions.le(pkeyProperty, boundingMax))
          //                            .uniqueResult();
          matchingCount = null // TODO
          end = boundingMax!!
        } else {
          // We got an id, so there's exactly batchSize results.
          matchingCount = batchSize
          end = batchEndPkey
        }

        // Get start pkey and scanned record count for this batch.
        val result = session.hibernateSession.createCriteria(entityClass())
            .setProjection(Projections.projectionList()
                .add(Projections.min(pkeyProperty))
                .add(Projections.rowCount()))
            .add(boundingMin)
            .add(Restrictions.le(pkeyProperty, end))
            .uniqueResult() as Array<Any?>
        val start = result[0].toString()
        val scannedCount = result[1] as Long

        TxResult(end,
            Batch.Builder()
                .batch_range(KeyRange.Builder()
                    .start(start.toString().encodeUtf8())
                    .end(end.toString().encodeUtf8())
                    .build())
                .scanned_record_count(scannedCount ?: 0)
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
    val config = BackfillConfig(request.parameters, request.dry_run)

    val pkeys = transactionForInstance<List<Pkey>>(request.instance_name, { session ->
      val minId = Id<E>(request.batch_range.start.utf8().toLong())
      val maxId = Id<E>(request.batch_range.end.utf8().toLong())
      val pkeyProperty = primaryKeyHibernateName()
      return@transactionForInstance session.hibernateSession.getExecutableCriteria(
          backfillCriteria(config))
          .setProjection(Projections.distinct(Projections.property(pkeyProperty)))
          .add(Restrictions.ge(pkeyProperty, minId))
          .add(Restrictions.le(pkeyProperty, maxId))
          .addOrder(Order.asc(pkeyProperty))
          .list();
    })
    runBatch(pkeys, config)

    return RunBatchResponse.Builder().build()
  }

  protected fun schemaAndTable(): String {
    val tableAnnotation = entityClass().getAnnotation(Table::class.java)
    val schema = tableAnnotation.schema
    val table = tableAnnotation.name
    return when {
      schema.isEmpty() -> "`$table`"
      else -> "`$schema`.`$table`"
    }
  }

  companion object {
    private val logger = getLogger<HibernateBackfill<*, *>>()
  }
}
