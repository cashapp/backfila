package app.cash.backfila.client.sqldelight

/**
 * Strategy for computing the bounding range max during batch generation.
 *
 * The default [DefaultBoundingRangeStrategy] uses the queries from [SqlDelightRecordSourceConfig]
 * directly, which works for unsharded databases.
 *
 * For Vitess sharded databases, implement a strategy that either:
 * - Runs queries per-shard (when using [VitessShardedPartitionProvider])
 * - Queries all shards in parallel and takes the minimum (single cursor approach)
 *
 * The latter approach works around Vitess's limitation that nested SELECT in FROM clauses
 * (like `SELECT MAX(id) FROM (SELECT DISTINCT id ... LIMIT n)`) can exceed the 100k row
 * scan limit when the WHERE clause spans a large range.
 */
interface BoundingRangeStrategy<K : Any> {
  /**
   * Computes the absolute min and max primary keys for the table.
   *
   * @param partitionName The partition (shard) to query, or "only" for unsharded.
   * @param queries The query config containing the selectAbsoluteRange query.
   * @return The min and max keys, or null values if the table is empty.
   */
  fun computeAbsoluteRange(
    partitionName: String,
    queries: SqlDelightRecordSourceConfig<K, *>,
  ): MinMax<K>

  /**
   * Computes the maximum key to bound the next batch scan.
   *
   * This is the query that can hit Vitess's 100k row limit when scanning large tables.
   * Vitess-specific implementations should either:
   * - Execute pinned to a single shard (each shard has fewer rows)
   * - Query all shards in parallel and return the minimum of the maxes
   *
   * @param partitionName The partition (shard) to query, or "only" for unsharded.
   * @param previousEndKey The end key of the previous batch, or null if this is the first batch.
   * @param rangeStart The start of the backfill range.
   * @param rangeEnd The end of the backfill range.
   * @param scanSize The number of records to scan for this bounding range.
   * @param queries The query config containing the bounding range queries.
   * @return The maximum key for this bounding range, or null if no more records.
   */
  fun computeBoundingRangeMax(
    partitionName: String,
    previousEndKey: K?,
    rangeStart: K,
    rangeEnd: K,
    scanSize: Long,
    queries: SqlDelightRecordSourceConfig<K, *>,
  ): K?
}

/**
 * Default bounding range strategy that executes queries directly.
 *
 * This works for unsharded databases or databases without Vitess's row count limits.
 * For Vitess, implement a strategy that handles the 100k row limit by either
 * partitioning by shard or querying shards in parallel.
 */
class DefaultBoundingRangeStrategy<K : Any> : BoundingRangeStrategy<K> {
  override fun computeAbsoluteRange(
    partitionName: String,
    queries: SqlDelightRecordSourceConfig<K, *>,
  ): MinMax<K> {
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
    return if (previousEndKey == null) {
      queries.selectInitialMaxBound(rangeStart, rangeEnd, scanSize).executeAsOne().key
    } else {
      queries.selectNextMaxBound(previousEndKey, rangeEnd, scanSize).executeAsOne().key
    }
  }
}
