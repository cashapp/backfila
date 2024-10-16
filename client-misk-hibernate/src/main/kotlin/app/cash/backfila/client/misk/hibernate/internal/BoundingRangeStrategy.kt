package app.cash.backfila.client.misk.hibernate.internal

import app.cash.backfila.client.misk.hibernate.HibernateBackfill
import app.cash.backfila.client.misk.hibernate.PartitionProvider
import com.google.common.collect.Ordering
import javax.persistence.Table
import misk.hibernate.DbEntity
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.shards
import misk.hibernate.transaction
import misk.vitess.Keyspace
import org.hibernate.internal.SessionImpl

/**
 * The queries that are provided by the strategy are used to establish a primary key slice of
 * the table off which the backfill criteria is applied.
 */
interface BoundingRangeStrategy<E : DbEntity<E>, Pkey : Any> {
  /**
   * Computes the raw table min and max based on the primary key. Returns null if the table is empty.
   */
  fun computeAbsoluteRange(
    backfill: HibernateBackfill<E, Pkey, *>,
    partitionName: String,
  ): MinMax<Pkey>?

  /**
   * Computes a bound of size request.scan_size, to get a set of records that can be scanned for
   * records that match the criteria.
   *
   * Returns null if there is are no more records left in the table.
   * The return value must be greater than or equal to [backfillRangeStart] and less than or equal
   * to [backfillRangeEnd] and greater than [previousEndKey].
   *
   * @param backfillRangeStart this is [MinMax.min] unless a specific range was specified.
   * @param backfillRangeEnd this is [MinMax.max] unless a specific range was specified.
   * @param previousEndKey is the null at the start or the result of a previous call to this function.
   */
  fun computeBoundingRangeMax(
    backfill: HibernateBackfill<E, Pkey, *>,
    partitionName: String,
    previousEndKey: Pkey?,
    backfillRangeStart: Pkey,
    backfillRangeEnd: Pkey,
    scanSize: Long?,
  ): Pkey?

  /**
   * Gets the min and count for the range of records.
   *
   * The returned [MinCount.min] value must be greater than [previousEndKey] and greater than or equal to [backfillRangeStart].
   * If [previousEndKey] is null:
   *   The returned [MinCount.scannedCount] counts items in [backfillRangeStart] (inclusive) until [end] (inclusive)
   *
   * If [previousEndKey] is non-null:
   *   The returned [MinCount.scannedCount] counts items in [previousEndKey] (exclusive) until  [end] (inclusive).
   *
   * @param backfillRangeStart this is [MinMax.min] unless a specific range was specified.
   * @param end is the batch slice and is greater than or equal to [backfillRangeStart].
   * @param previousEndKey is the null at the start or the result of a previous call [computeBoundingRangeMax].
   */
  fun computeMinAndCountForRange(
    backfill: HibernateBackfill<E, Pkey, *>,
    session: Session,
    previousEndKey: Pkey?,
    backfillRangeStart: Pkey,
    end: Pkey,
  ): MinCount<Pkey>
}

class UnshardedHibernateBoundingRangeStrategy<E : DbEntity<E>, Pkey : Any>(
  private val partitionProvider: PartitionProvider,
) : BoundingRangeStrategy<E, Pkey> {
  override fun computeAbsoluteRange(
    backfill: HibernateBackfill<E, Pkey, *>,
    partitionName: String,
  ): MinMax<Pkey>? {
    return partitionProvider.transaction(partitionName) { session ->
      selectMinAndMax(
        backfill,
        session,
        schemaAndTable(backfill),
      )
    }
  }

  override fun computeBoundingRangeMax(
    backfill: HibernateBackfill<E, Pkey, *>,
    partitionName: String,
    previousEndKey: Pkey?,
    backfillRangeStart: Pkey,
    backfillRangeEnd: Pkey,
    scanSize: Long?,
  ): Pkey? {
    return partitionProvider.transaction(partitionName) { session ->
      selectMaxBound(
        backfill = backfill,
        session = session,
        schemaAndTable = schemaAndTable(backfill),
        previousEndKey = previousEndKey,
        backfillRangeStart = backfillRangeStart,
        backfillRangeEnd = backfillRangeEnd,
        scanSize = scanSize,
      )
    }
  }

  override fun computeMinAndCountForRange(
    backfill: HibernateBackfill<E, Pkey, *>,
    session: Session,
    previousEndKey: Pkey?,
    backfillRangeStart: Pkey,
    end: Pkey,
  ): MinCount<Pkey> {
    return selectMinAndCount(
      backfill = backfill,
      session = session,
      schemaAndTable = schemaAndTable(backfill),
      previousEndKey = previousEndKey,
      backfillRangeStart = backfillRangeStart,
      end = end,
    )
  }
}

class VitessShardedBoundingRangeStrategy<E : DbEntity<E>, Pkey : Any>(
  private val partitionProvider: PartitionProvider,
) : BoundingRangeStrategy<E, Pkey> {
  override fun computeAbsoluteRange(
    backfill: HibernateBackfill<E, Pkey, *>,
    partitionName: String,
  ): MinMax<Pkey>? {
    return partitionProvider.transaction(partitionName) { session ->
      selectMinAndMax(
        backfill,
        session,
        onlyTable(backfill),
      )
    }
  }

  override fun computeBoundingRangeMax(
    backfill: HibernateBackfill<E, Pkey, *>,
    partitionName: String,
    previousEndKey: Pkey?,
    backfillRangeStart: Pkey,
    backfillRangeEnd: Pkey,
    scanSize: Long?,
  ): Pkey? {
    return partitionProvider.transaction(partitionName) { session ->
      // We don't provide a schema when pinned to a shard.
      selectMaxBound(
        backfill = backfill,
        session = session,
        schemaAndTable = onlyTable(backfill),
        previousEndKey = previousEndKey,
        backfillRangeStart = backfillRangeStart,
        backfillRangeEnd = backfillRangeEnd,
        scanSize = scanSize,
      )
    }
  }

  override fun computeMinAndCountForRange(
    backfill: HibernateBackfill<E, Pkey, *>,
    session: Session,
    previousEndKey: Pkey?,
    backfillRangeStart: Pkey,
    end: Pkey,
  ): MinCount<Pkey> {
    return selectMinAndCount(
      backfill = backfill,
      session = session,
      schemaAndTable = onlyTable(backfill),
      previousEndKey = previousEndKey,
      backfillRangeStart = backfillRangeStart,
      end = end,
    )
  }
}

class VitessSingleCursorBoundingRangeStrategy<E : DbEntity<E>, Pkey : Any>(
  private val transacter: Transacter,
  private val keyspace: Keyspace,
) : BoundingRangeStrategy<E, Pkey> {
  override fun computeAbsoluteRange(
    backfill: HibernateBackfill<E, Pkey, *>,
    partitionName: String,
  ): MinMax<Pkey>? {
    return transacter.transaction { session ->
      selectMinAndMax(
        backfill,
        session,
        onlyTable(backfill),
      )
    }
  }

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
    backfill: HibernateBackfill<E, Pkey, *>,
    partitionName: String,
    previousEndKey: Pkey?,
    backfillRangeStart: Pkey,
    backfillRangeEnd: Pkey,
    scanSize: Long?,
  ): Pkey? {
    return transacter.shards(keyspace).parallelStream().map {
      transacter.transaction(it) { session ->
        // We don't provide a schema when pinned to a shard.
        selectMaxBound(
          backfill = backfill,
          session = session,
          schemaAndTable = onlyTable(backfill),
          previousEndKey = previousEndKey,
          backfillRangeStart = backfillRangeStart,
          backfillRangeEnd = backfillRangeEnd,
          scanSize = scanSize,
        )
      }
    }.toList()
      .filterNotNull()
      // Pkey must have a natural ordering
      .minWithOrNull(Ordering.natural<Comparable<Pkey>>() as Comparator<Pkey>)
  }

  override fun computeMinAndCountForRange(
    backfill: HibernateBackfill<E, Pkey, *>,
    session: Session,
    previousEndKey: Pkey?,
    backfillRangeStart: Pkey,
    end: Pkey,
  ): MinCount<Pkey> {
    return selectMinAndCount(
      backfill = backfill,
      session = session,
      schemaAndTable = onlyTable(backfill),
      previousEndKey = previousEndKey,
      backfillRangeStart = backfillRangeStart,
      end = end,
    )
  }
}

class SingleCursorVitess

private fun <E : DbEntity<E>, Pkey : Any> selectMinAndMax(
  backfill: HibernateBackfill<E, Pkey, *>,
  session: Session,
  schemaAndTable: String,
): MinMax<Pkey>? {
  // This query uses raw sql to avoid bumping into hibernate features such as @Where and
  // @SQLRestriction.
  // All of [selectMaxBound], [selectMinAndMax] and [selectMinAndCount] must be raw SQL since
  // they depend on each other having the same view of the table.
  val pkeyName = backfill.primaryKeyName()
  val sql = """
        |SELECT MIN($pkeyName) as min, MAX($pkeyName) as max 
        |FROM $schemaAndTable
  """.trimMargin()
  val minMax = session.useConnection { connection ->
    connection.prepareStatement(sql).use { ps ->
      val pkeyType = session.hibernateSession.typeHelper.basic(backfill.pkeyClass.java)!!

      val rs = ps.executeQuery()
      rs.next()
      val min = pkeyType.nullSafeGet(rs, "min", session.hibernateSession as SessionImpl, null)
      val max = pkeyType.nullSafeGet(rs, "max", session.hibernateSession as SessionImpl, null)
      if (min == null) {
        // Empty table, no work to do for this partition.
        return@use null
      } else {
        checkNotNull(max) { "Table max was null but min wasn't, this shouldn't happen" }
        @Suppress("UNCHECKED_CAST") // Return type from the query should always be Pkey.
        MinMax(min as Pkey, max as Pkey)
      }
    }
  }
  return minMax
}

data class MinMax<Pkey : Any>(
  val min: Pkey,
  val max: Pkey,
)

private fun <E : DbEntity<E>, Pkey : Any> selectMaxBound(
  backfill: HibernateBackfill<E, Pkey, *>,
  session: Session,
  schemaAndTable: String,
  previousEndKey: Pkey?,
  backfillRangeStart: Pkey,
  backfillRangeEnd: Pkey,
  scanSize: Long?,
): Pkey? {
  // Hibernate doesn't support subqueries in FROM, and we don't want to read in 100k+ records,
  // so we use raw SQL here.
  // All of [selectMaxBound], [selectMinAndMax] and [selectMinAndCount] must be raw SQL since
  // they depend on each other having the same view of the table.
  val pkeyName = backfill.primaryKeyName()
  val params = mutableListOf<Pkey>()
  var where = when {
    previousEndKey != null -> {
      params.add(previousEndKey)
      "WHERE $pkeyName > ?"
    }
    else -> {
      params.add(backfillRangeStart)
      "WHERE $pkeyName >= ?"
    }
  }
  params.add(backfillRangeEnd)
  where += " AND $pkeyName <= ?"

  val sql = """
        |SELECT MAX(s.$pkeyName) as result FROM
        | (SELECT DISTINCT $pkeyName FROM $schemaAndTable
        | $where
        | ORDER BY $pkeyName
        | LIMIT $scanSize) s
  """.trimMargin()

  val max = session.useConnection { connection ->
    connection.prepareStatement(sql).use { ps ->
      val pkeyType = session.hibernateSession.typeHelper.basic(backfill.pkeyClass.java)!!

      params.forEachIndexed { index, pkey ->
        pkeyType.nullSafeSet(ps, pkey, index + 1, session.hibernateSession as SessionImpl)
      }

      val rs = ps.executeQuery()
      rs.next()
      pkeyType.nullSafeGet(rs, "result", session.hibernateSession as SessionImpl, null)
    }
  }

  @Suppress("UNCHECKED_CAST") // Return type from the query should always match.
  return max as Pkey?
}

private fun <E : DbEntity<E>, Pkey : Any> selectMinAndCount(
  backfill: HibernateBackfill<E, Pkey, *>,
  session: Session,
  schemaAndTable: String,
  previousEndKey: Pkey?,
  backfillRangeStart: Pkey,
  end: Pkey,
): MinCount<Pkey> {
  // This query uses raw sql to avoid bumping into hibernate features such as @Where and
  // @SQLRestriction.
  // All of [selectMaxBound], [selectMinAndMax] and [selectMinAndCount] must be raw SQL since
  // they depend on each other having the same view of the table.
  val pkeyName = backfill.primaryKeyName()
  val params = mutableListOf<Pkey>()
  var where = when {
    previousEndKey != null -> {
      params.add(previousEndKey)
      "WHERE $pkeyName > ?"
    }
    else -> {
      params.add(backfillRangeStart)
      "WHERE $pkeyName >= ?"
    }
  }
  params.add(end)
  where += " AND $pkeyName <= ?"
  val sql = """
              |SELECT MIN($pkeyName) as start, COUNT(*) as scannedCount 
              |FROM $schemaAndTable
              |$where
  """.trimMargin()
  val minCount = session.useConnection { connection ->
    connection.prepareStatement(sql).use { ps ->
      val pkeyType = session.hibernateSession.typeHelper.basic(backfill.pkeyClass.java)!!

      params.forEachIndexed { index, pkey ->
        pkeyType.nullSafeSet(ps, pkey, index + 1, session.hibernateSession as SessionImpl)
      }

      val rs = ps.executeQuery()
      rs.next()
      @Suppress("UNCHECKED_CAST") // Return type from the query should always be a Pkey and Long.
      MinCount(
        pkeyType.nullSafeGet(rs, "start", session.hibernateSession as SessionImpl, null) as Pkey,
        rs.getLong("scannedCount"),
      )
    }
  }
  return minCount
}

data class MinCount<Pkey : Any>(
  val min: Pkey,
  val scannedCount: Long,
)

private fun <E : DbEntity<E>, Pkey : Any> schemaAndTable(backfill: HibernateBackfill<E, Pkey, *>): String {
  val tableAnnotation = backfill.entityClass.java.getAnnotation(Table::class.java)
  val schema = tableAnnotation.schema
  val table = tableAnnotation.name.ifEmpty {
    error(
      """
      Entity class ${backfill.entityClass.simpleName} is missing Table name.
      
      We require a table name to encourage best practices. An entity is singular while your table
      is plural. Additionally, you probably want to name your classes in a way to make them
      obviously entity classes. Such as prefixing all entities with `DB`.
      
      You are welcome to create a copy of the bounding range strategy limited to your service
      if you absolutely cannot have table name annotation on your entity class.
      """.trimIndent(),
    )
  }
  return when {
    schema.isEmpty() -> "`$table`"
    else -> "`$schema`.`$table`"
  }
}

private fun <E : DbEntity<E>, Pkey : Any> onlyTable(backfill: HibernateBackfill<E, Pkey, *>): String {
  val tableAnnotation = backfill.entityClass.java.getAnnotation(Table::class.java)
  val table = tableAnnotation.name.ifEmpty {
    error(
      """
      Entity class ${backfill.entityClass.simpleName} is missing Table name.
      
      We require a table name to encourage best practices. An entity is singular while your table
      is plural. Additionally, you probably want to name your classes in a way to make them
      obviously entity classes. Such as prefixing all entities with `DB`.
      
      You are welcome to create a copy of the bounding range strategy limited to your service
      if you absolutely cannot have table name annotation on your entity class.
      """.trimIndent(),
    )
  }
  return "`$table`"
}
