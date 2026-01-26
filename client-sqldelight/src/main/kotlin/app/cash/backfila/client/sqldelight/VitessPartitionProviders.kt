package app.cash.backfila.client.sqldelight

import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import misk.jdbc.DataSourceService
import misk.vitess.Keyspace
import misk.vitess.Shard
import misk.vitess.ShardsLoader

/**
 * A sharded partition provider that creates a Backfila partition per Vitess shard.
 *
 * Since a cursor is maintained for each shard separately, if entities are moved between shards
 * it's possible they will be missed by the backfill. Also at minimum one thread is used per shard
 * since each shard is its own partition. If your entities can move, or if you need to run this
 * backfill slower than one thread per shard, consider using [VitessSingleCursorPartitionProvider]
 * instead.
 *
 * Usage:
 * ```kotlin
 * class MyBackfill @Inject constructor(
 *   myDatabase: MyDatabase,
 *   @MyDb dataSourceService: DataSourceService,
 * ) : SqlDelightDatasourceBackfill<Long, MyRecord, NoParameters>(
 *   MyRecordSourceConfig(myDatabase),
 * ) {
 *   override fun partitionProvider() = VitessShardedPartitionProvider(
 *     dataSourceService,
 *     Keyspace("my_keyspace"),
 *   )
 * }
 * ```
 *
 * @param dataSourceService The Misk DataSourceService for the database
 * @param keyspace The Vitess keyspace to get shards from
 */
class VitessShardedPartitionProvider(
  private val dataSourceService: DataSourceService,
  private val keyspace: Keyspace,
) : PartitionProvider {
  private val shardsSupplier = ShardsLoader.shards(dataSourceService)

  override fun names(request: PrepareBackfillRequest): List<String> =
    shardsSupplier.get()
      .filter { it.keyspace == keyspace }
      .map { it.name }

  override fun <T> transaction(partitionName: String, task: () -> T): T {
    // For sharded execution, the bounding range strategy handles per-shard queries.
    return task()
  }

  /**
   * Returns a Vitess-aware bounding range strategy.
   *
   * Note: For Vitess strategies, K must be Comparable for the min/max operations.
   * Primary keys are always comparable (Long, Int, String, etc.).
   */
  @Suppress("UNCHECKED_CAST")
  override fun <K : Any> boundingRangeStrategy(): BoundingRangeStrategy<K> {
    // Vitess strategies require Comparable keys for min/max operations.
    // This cast is safe because primary keys are always comparable.
    return VitessShardedBoundingRangeStrategy<Nothing>(dataSourceService, keyspace) as BoundingRangeStrategy<K>
  }

  /**
   * Returns all shards for this keyspace.
   */
  fun shards(): Set<Shard> =
    shardsSupplier.get().filter { it.keyspace == keyspace }.toSet()
}

/**
 * A partition provider that iterates over sharded Vitess using a single cursor.
 *
 * This can only be used if primary keys are unique across all shards, e.g. using a Vitess sequence.
 *
 * Prefer [VitessShardedPartitionProvider]
 *
 * The benefits of this vs [VitessShardedPartitionProvider] is that if entities move from one
 * shard to another, they will not be missed because one shard's cursor cannot get ahead of
 * another's. It is also indifferent to shard splits and can run slower than one thread per shard.
 * The disadvantage is less efficient concurrency, since batches are computed by scanning all shards
 * each time, rather than splitting the work by shard.
 *
 * Usage:
 * ```kotlin
 * class MyBackfill @Inject constructor(
 *   myDatabase: MyDatabase,
 *   @MyDb dataSourceService: DataSourceService,
 * ) : SqlDelightDatasourceBackfill<Long, MyRecord, NoParameters>(
 *   MyRecordSourceConfig(myDatabase),
 * ) {
 *   override fun partitionProvider() = VitessSingleCursorPartitionProvider(
 *     dataSourceService,
 *     Keyspace("my_keyspace"),
 *   )
 * }
 * ```
 *
 * @param dataSourceService The Misk DataSourceService for the database
 * @param keyspace The Vitess keyspace
 */
class VitessSingleCursorPartitionProvider(
  private val dataSourceService: DataSourceService,
  private val keyspace: Keyspace,
) : PartitionProvider {
  private val shardsSupplier = ShardsLoader.shards(dataSourceService)

  override fun names(request: PrepareBackfillRequest) = listOf("only")

  override fun <T> transaction(partitionName: String, task: () -> T): T = task()

  @Suppress("UNCHECKED_CAST")
  override fun <K : Any> boundingRangeStrategy(): BoundingRangeStrategy<K> {
    // Vitess strategies require Comparable keys for min/max operations.
    // This cast is safe because primary keys are always comparable.
    return VitessSingleCursorBoundingRangeStrategy<Nothing>(dataSourceService, keyspace) as BoundingRangeStrategy<K>
  }

  /**
   * Returns all shards for this keyspace.
   */
  fun shards(): Set<Shard> =
    shardsSupplier.get().filter { it.keyspace == keyspace }.toSet()
}
