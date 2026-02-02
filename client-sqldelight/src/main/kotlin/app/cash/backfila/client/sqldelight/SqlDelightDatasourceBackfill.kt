package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.Backfill
import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.PrepareBackfillConfig

/**
 * Base class for SqlDelight-based backfills.
 *
 * K - Key type (primary key type, must be Comparable for Vitess strategies)
 * R - Row type (the record returned by SqlDelight queries)
 * P - Parameters type (user-defined parameters for the backfill)
 *
 * For unsharded databases, simply extend this class:
 * ```kotlin
 * class MyBackfill @Inject constructor(
 *   myDatabase: MyDatabase,
 * ) : SqlDelightDatasourceBackfill<Long, MyRecord, NoParameters>(
 *   MyRecordSourceConfig(myDatabase),
 * )
 * ```
 *
 * For Vitess sharded databases, override [partitionProvider]:
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
 * @param recordSourceConfig The SqlDelight query config for this backfill.
 */
abstract class SqlDelightDatasourceBackfill<K : Any, R : Any, P : Any>(
  val recordSourceConfig: SqlDelightRecordSourceConfig<K, R>,
) : Backfill {

  /**
   * Returns the partition provider for this backfill.
   *
   * Override this to use Vitess-aware partition providers like [VitessShardedPartitionProvider]
   * or [VitessSingleCursorPartitionProvider].
   *
   * The partition provider also determines the [BoundingRangeStrategy] via its
   * [PartitionProvider.boundingRangeStrategy] method.
   */
  open fun partitionProvider(): PartitionProvider = UnshardedPartitionProvider()

  /**
   * Override this and throw an exception to prevent the backfill from being created.
   * This is also a good place to do any prep work before batches are run.
   */
  open fun validate(config: PrepareBackfillConfig<P>) {}

  /**
   * Called for each batch of records.
   * Override in a backfill to process all records in a batch.
   */
  open fun runBatch(records: List<@JvmSuppressWildcards R>, config: BackfillConfig<P>) {
    records.forEach { runOne(it, config) }
  }

  /**
   * Called for each record.
   * Override in a backfill to process one record at a time.
   */
  open fun runOne(record: R, config: BackfillConfig<P>) {
  }
}
