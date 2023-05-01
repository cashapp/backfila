package app.cash.backfila.client.sqldelight

import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.sqldelight.Transacter

/**
 * Provides connectivity to a singleton database or a set of database shards.
 */
interface PartitionProvider<D : Transacter> {
  /**
   * Names the databases that will be connected with [transaction]. In a Vitess environment these
   * are the shard names.
   */
  fun names(request: PrepareBackfillRequest): List<String>

  fun <T> withDatabase(partitionName: String, task: (D) -> T): T

  // TODO: bounding range strategy?
}
