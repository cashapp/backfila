package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.PartitionProvider
import app.cash.backfila.protos.clientservice.KeyRange
import com.google.common.collect.Ordering
import javax.persistence.Table
import kotlin.streams.toList
import misk.hibernate.DbEntity
import misk.hibernate.Keyspace
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.shards
import misk.hibernate.transaction
import okio.ByteString

interface BoundingRangeStrategy<E : DbEntity<E>, Pkey : Any> {
  /**
   * Computes a bound of size request.scan_size, to get a set of records that can be scanned for
   * records that match the criteria.
   * Returns null if there is are no more records left in the table.
   */
  fun computeBoundingRangeMax(
    backfill: Backfill<E, Pkey>,
    partitionName: String,
    previousEndKey: ByteString?,
    backfillRange: KeyRange,
    scanSize: Long?
  ): Pkey?
}

class UnshardedHibernateBoundingRangeStrategy<E : DbEntity<E>, Pkey : Any> (
  private val partitionProvider: PartitionProvider
) : BoundingRangeStrategy<E, Pkey> {
  override fun computeBoundingRangeMax(
    backfill: Backfill<E, Pkey>,
    partitionName: String,
    previousEndKey: ByteString?,
    backfillRange: KeyRange,
    scanSize: Long?
  ): Pkey? {
    return partitionProvider.transaction(partitionName) { session ->
      selectMaxBound(backfill, session, schemaAndTable(backfill), previousEndKey, backfillRange, scanSize)
    }
  }
}

class VitessShardedBoundingRangeStrategy<E : DbEntity<E>, Pkey : Any> (
  private val partitionProvider: PartitionProvider
) : BoundingRangeStrategy<E, Pkey> {
  override fun computeBoundingRangeMax(
    backfill: Backfill<E, Pkey>,
    partitionName: String,
    previousEndKey: ByteString?,
    backfillRange: KeyRange,
    scanSize: Long?
  ): Pkey? {
    return partitionProvider.transaction(partitionName) { session ->
      // We don't provide a schema when pinned to a shard.
      selectMaxBound(backfill, session, onlyTable(backfill), previousEndKey, backfillRange, scanSize)
    }
  }
}

class VitessSingleCursorBoundingRangeStrategy<E : DbEntity<E>, Pkey : Any> (
  private val transacter: Transacter,
  private val keyspace: Keyspace
) : BoundingRangeStrategy<E, Pkey> {

  /**
   * Computes a bounding range by scanning all shards and returning the minimum of MAX(pkey).
   *
   * Vitess does not support the nested select in `SELECT MAX(s.id) FROM (subquery)` used in
   * [UnshardedHibernateBoundingRangeStrategy]. This is fine for backfills that run on each shard
   * independently, as in [VitessShardedBoundingRangeStrategy]. To workaround for all-shard
   * backfills we have to call SELECT MAX per shard, then pick the minimum value, ensuring we have
   * at least bound_size records in the bound, but up to shard_count*bound_size in the bound. The
   * former when the next scan_size records are on one shard, and the latter when they are evenly
   * distributed across all shards.
   */
  override fun computeBoundingRangeMax(
    backfill: Backfill<E, Pkey>,
    partitionName: String,
    previousEndKey: ByteString?,
    backfillRange: KeyRange,
    scanSize: Long?
  ): Pkey? {
    return transacter.shards(keyspace).parallelStream().map {
      transacter.transaction(it) {
        // We don't provide a schema when pinned to a shard.
        session -> selectMaxBound(backfill, session, onlyTable(backfill), previousEndKey, backfillRange, scanSize)
      }
    }.toList()
        .filterNotNull()
        // Pkey must have a natural ordering
        .minWith(Ordering.natural<Comparable<Pkey>>() as Comparator<Pkey>)
  }
}

class SingleCursorVitess

private fun <E : DbEntity<E>, Pkey : Any> selectMaxBound(
  backfill: Backfill<E, Pkey>,
  session: Session,
  schemaAndTable: String,
  previousEndKey: ByteString?,
  backfillRange: KeyRange,
  scanSize: Long?
): Pkey? {
  // Hibernate doesn't support subqueries in FROM, and we don't want to read in 100k+ records,
  // so we use raw SQL here.
  val pkeyName = backfill.primaryKeyName()
  var where = when {
    previousEndKey != null -> "WHERE $pkeyName > ${previousEndKey.utf8()}"
    else -> "WHERE $pkeyName >= ${backfillRange.start.utf8()}"
  }
  where += " AND $pkeyName <= ${backfillRange.end.utf8()}"
  val sql = """
        |SELECT MAX(s.$pkeyName) FROM
        | (SELECT DISTINCT $pkeyName FROM $schemaAndTable
        | $where
        | ORDER BY $pkeyName
        | LIMIT $scanSize) s
        """.trimMargin()
  val max = session.hibernateSession.createNativeQuery(sql).uniqueResult()

  @Suppress("UNCHECKED_CAST") // Return type from the query should always match.
  return max as Pkey? // I think we are always getting a Pkey here so the cast should be safe.
}

private fun <E : DbEntity<E>, Pkey : Any> schemaAndTable(backfill: Backfill<E, Pkey>): String {
  val tableAnnotation = backfill.entityClass.java.getAnnotation(Table::class.java)
  val schema = tableAnnotation.schema
  val table = tableAnnotation.name
  return when {
    schema.isEmpty() -> "`$table`"
    else -> "`$schema`.`$table`"
  }
}

private fun <E : DbEntity<E>, Pkey : Any> onlyTable(backfill: Backfill<E, Pkey>): String {
  val tableAnnotation = backfill.entityClass.java.getAnnotation(Table::class.java)
  val table = tableAnnotation.name
  return "`$table`"
}
