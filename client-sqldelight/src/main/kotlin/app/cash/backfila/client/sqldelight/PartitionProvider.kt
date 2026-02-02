package app.cash.backfila.client.sqldelight

import app.cash.backfila.protos.clientservice.PrepareBackfillRequest

/**
 * Provides connectivity to a singleton database or a set of database shards.
 *
 * For unsharded databases, use [UnshardedPartitionProvider] which returns a single "only" partition.
 *
 * For Vitess sharded databases, use [VitessShardedPartitionProvider] or
 * [VitessSingleCursorPartitionProvider]. These avoid Vitess's 100k row scan limit
 * by processing each shard independently.
 */
interface PartitionProvider {
  /**
   * Names the databases that will be connected with [transaction]. In a Vitess environment these
   * are the shard names (e.g., ["-40", "40-80", "80-c0", "c0-"]).
   *
   * For unsharded databases, return ["only"].
   */
  fun names(request: PrepareBackfillRequest): List<String>

  /**
   * Executes work within a transaction for the given partition.
   *
   * For sharded databases, this should pin the transaction to the specific shard.
   * For unsharded databases, this executes on the single database.
   */
  fun <T> transaction(partitionName: String, task: () -> T): T

  /**
   * Returns the bounding range strategy to use for computing batch ranges.
   *
   * The partition provider includes this method so that Vitess partition providers can
   * automatically return the appropriate Vitess-aware strategy.
   */
  fun <K : Any> boundingRangeStrategy(): BoundingRangeStrategy<K>
}

/**
 * A simple unsharded partition provider that uses a single Backfila partition.
 *
 * If you are using a Vitess datasource, you should use [VitessShardedPartitionProvider]
 * or [VitessSingleCursorPartitionProvider] instead.
 */
class UnshardedPartitionProvider : PartitionProvider {
  override fun names(request: PrepareBackfillRequest) = listOf("only")

  override fun <T> transaction(partitionName: String, task: () -> T): T = task()

  override fun <K : Any> boundingRangeStrategy(): BoundingRangeStrategy<K> =
    DefaultBoundingRangeStrategy()
}
