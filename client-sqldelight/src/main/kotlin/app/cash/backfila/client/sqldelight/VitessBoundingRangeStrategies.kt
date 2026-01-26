package app.cash.backfila.client.sqldelight

import java.sql.Connection
import java.sql.PreparedStatement
import java.util.stream.Collectors
import misk.jdbc.DataSourceService
import misk.vitess.Keyspace
import misk.vitess.Shard
import misk.vitess.ShardsLoader

/**
 * A bounding range strategy for sharded Vitess backfills.
 *
 * This strategy runs queries targeting each shard independently, avoiding the Vitess
 * row scan limit by ensuring each query only scans rows on a single shard.
 *
 * Use with [VitessShardedPartitionProvider].
 *
 * @param dataSourceService The Misk DataSourceService for the database
 * @param keyspace The Vitess keyspace
 */
class VitessShardedBoundingRangeStrategy<K : Any>(
  private val dataSourceService: DataSourceService,
  private val keyspace: Keyspace,
) : BoundingRangeStrategy<K> {

  override fun computeAbsoluteRange(
    partitionName: String,
    queries: SqlDelightRecordSourceConfig<K, *>,
  ): MinMax<K> {
    val tableName = requireNotNull(queries.tableName()) {
      "tableName() must be implemented in SqlDelightRecordSourceConfig for Vitess strategies"
    }
    val pkeyColumn = requireNotNull(queries.primaryKeyColumn()) {
      "primaryKeyColumn() must be implemented in SqlDelightRecordSourceConfig for Vitess strategies"
    }

    return dataSourceService.dataSource.connection.use { connection ->
      // Target the specific shard
      targetShard(connection, partitionName)
      selectMinAndMax(connection, tableName, pkeyColumn)
    }
  }

  override fun computeBoundingRangeMax(
    partitionName: String,
    previousEndKey: K?,
    rangeStart: K,
    rangeEnd: K,
    scanSize: Long,
    queries: SqlDelightRecordSourceConfig<K, *>,
  ): K? {
    val tableName = requireNotNull(queries.tableName()) {
      "tableName() must be implemented in SqlDelightRecordSourceConfig for Vitess strategies"
    }
    val pkeyColumn = requireNotNull(queries.primaryKeyColumn()) {
      "primaryKeyColumn() must be implemented in SqlDelightRecordSourceConfig for Vitess strategies"
    }

    return dataSourceService.dataSource.connection.use { connection ->
      // Target the specific shard
      targetShard(connection, partitionName)
      selectMaxBound(connection, tableName, pkeyColumn, previousEndKey, rangeStart, rangeEnd, scanSize)
    }
  }

  private fun targetShard(connection: Connection, shardName: String) {
    connection.createStatement().use { stmt ->
      stmt.execute("USE `${keyspace.name}:$shardName`")
    }
  }

  private fun selectMinAndMax(
    connection: Connection,
    tableName: String,
    pkeyColumn: String,
  ): MinMax<K> {
    val sql = "SELECT MIN(`$pkeyColumn`) as min_val, MAX(`$pkeyColumn`) as max_val FROM `$tableName`"
    connection.prepareStatement(sql).use { ps ->
      val rs = ps.executeQuery()
      if (rs.next()) {
        val min = rs.getObject("min_val")
        val max = rs.getObject("max_val")
        if (min == null && max == null) {
          return MinMax(null, null)
        }
        @Suppress("UNCHECKED_CAST")
        return MinMax(min as K?, max as K?)
      }
      return MinMax(null, null)
    }
  }

  private fun selectMaxBound(
    connection: Connection,
    tableName: String,
    pkeyColumn: String,
    previousEndKey: K?,
    rangeStart: K,
    rangeEnd: K,
    scanSize: Long,
  ): K? {
    val whereClause = if (previousEndKey != null) {
      "`$pkeyColumn` > ? AND `$pkeyColumn` <= ?"
    } else {
      "`$pkeyColumn` >= ? AND `$pkeyColumn` <= ?"
    }

    val sql = """
      SELECT MAX(s.`$pkeyColumn`) as result FROM
        (SELECT DISTINCT `$pkeyColumn` FROM `$tableName`
         WHERE $whereClause
         ORDER BY `$pkeyColumn`
         LIMIT $scanSize) s
    """.trimIndent()

    connection.prepareStatement(sql).use { ps ->
      if (previousEndKey != null) {
        setKeyParameter(ps, 1, previousEndKey)
        setKeyParameter(ps, 2, rangeEnd)
      } else {
        setKeyParameter(ps, 1, rangeStart)
        setKeyParameter(ps, 2, rangeEnd)
      }

      val rs = ps.executeQuery()
      if (rs.next()) {
        @Suppress("UNCHECKED_CAST")
        return rs.getObject("result") as K?
      }
      return null
    }
  }

  private fun setKeyParameter(ps: PreparedStatement, index: Int, key: K) {
    when (key) {
      is Long -> ps.setLong(index, key)
      is Int -> ps.setInt(index, key)
      is String -> ps.setString(index, key)
      else -> ps.setObject(index, key)
    }
  }
}

/**
 * A bounding range strategy that queries all Vitess shards in parallel and returns the minimum.
 *
 * Vitess does not support the nested select in `SELECT MAX(id) FROM (subquery)` across all shards.
 * This strategy works around that by querying each shard separately, then picking the minimum
 * value across all shards.
 *
 * Use with [VitessSingleCursorPartitionProvider].
 *
 * Note: The key type K must be Comparable in practice (e.g., Long, Int, String) for the
 * min operation to work correctly. Primary keys are always comparable.
 *
 * @param dataSourceService The Misk DataSourceService for the database
 * @param keyspace The Vitess keyspace
 */
class VitessSingleCursorBoundingRangeStrategy<K : Any>(
  private val dataSourceService: DataSourceService,
  private val keyspace: Keyspace,
) : BoundingRangeStrategy<K> {
  private val shardsSupplier = ShardsLoader.shards(dataSourceService)

  override fun computeAbsoluteRange(
    partitionName: String,
    queries: SqlDelightRecordSourceConfig<K, *>,
  ): MinMax<K> {
    // For absolute range, we can use the standard query across all shards
    return queries.selectAbsoluteRange().executeAsOneOrNull() ?: MinMax(null, null)
  }

  override fun computeBoundingRangeMax(
    partitionName: String,
    previousEndKey: K?,
    rangeStart: K,
    rangeEnd: K,
    scanSize: Long,
    queries: SqlDelightRecordSourceConfig<K, *>,
  ): K? {
    val tableName = requireNotNull(queries.tableName()) {
      "tableName() must be implemented in SqlDelightRecordSourceConfig for Vitess strategies"
    }
    val pkeyColumn = requireNotNull(queries.primaryKeyColumn()) {
      "primaryKeyColumn() must be implemented in SqlDelightRecordSourceConfig for Vitess strategies"
    }

    val shards = shardsSupplier.get().filter { it.keyspace == keyspace }

    // Query each shard in parallel and take the minimum
    val results = shards.parallelStream().map { shard ->
      dataSourceService.dataSource.connection.use { connection ->
        targetShard(connection, shard)
        selectMaxBound(connection, tableName, pkeyColumn, previousEndKey, rangeStart, rangeEnd, scanSize)
      }
    }.collect(Collectors.toList())
      .filterNotNull()

    // Primary keys are always Comparable (Long, Int, String, etc.)
    // Use unchecked cast to Comparable for the min operation
    @Suppress("UNCHECKED_CAST")
    return (results as List<Comparable<Any>>).minWithOrNull(naturalOrder()) as K?
  }

  private fun targetShard(connection: Connection, shard: Shard) {
    connection.createStatement().use { stmt ->
      stmt.execute("USE `${shard.keyspace.name}:${shard.name}`")
    }
  }

  private fun selectMaxBound(
    connection: Connection,
    tableName: String,
    pkeyColumn: String,
    previousEndKey: K?,
    rangeStart: K,
    rangeEnd: K,
    scanSize: Long,
  ): K? {
    val whereClause = if (previousEndKey != null) {
      "`$pkeyColumn` > ? AND `$pkeyColumn` <= ?"
    } else {
      "`$pkeyColumn` >= ? AND `$pkeyColumn` <= ?"
    }

    val sql = """
      SELECT MAX(s.`$pkeyColumn`) as result FROM
        (SELECT DISTINCT `$pkeyColumn` FROM `$tableName`
         WHERE $whereClause
         ORDER BY `$pkeyColumn`
         LIMIT $scanSize) s
    """.trimIndent()

    connection.prepareStatement(sql).use { ps ->
      if (previousEndKey != null) {
        setKeyParameter(ps, 1, previousEndKey)
        setKeyParameter(ps, 2, rangeEnd)
      } else {
        setKeyParameter(ps, 1, rangeStart)
        setKeyParameter(ps, 2, rangeEnd)
      }

      val rs = ps.executeQuery()
      if (rs.next()) {
        @Suppress("UNCHECKED_CAST")
        return rs.getObject("result") as K?
      }
      return null
    }
  }

  private fun setKeyParameter(ps: PreparedStatement, index: Int, key: K) {
    when (key) {
      is Long -> ps.setLong(index, key)
      is Int -> ps.setInt(index, key)
      is String -> ps.setString(index, key)
      else -> ps.setObject(index, key)
    }
  }
}
