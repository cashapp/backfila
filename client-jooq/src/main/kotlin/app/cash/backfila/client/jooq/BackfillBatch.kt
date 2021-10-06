package app.cash.backfila.client.jooq

import app.cash.backfila.client.BackfillConfig

/**
 * Encapsulates the data we pass to the `batchConsumer` callback.
 */
data class BackfillBatch<K, Param : Any> (
  /**
   * The name of the database shard.
   */
  val shardName: String,

  /**
   * A jooq transacter to transact with the database shard.
   */
  val transacter: BackfillJooqTransacter,

  /**
   * The keys that should be processed to perform the backfill action.
   */
  val keys: List<K>,

  /**
   * Backfill configuration.
   */
  val config: BackfillConfig<Param>
)
