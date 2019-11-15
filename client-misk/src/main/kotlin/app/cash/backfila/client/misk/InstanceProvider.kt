package app.cash.backfila.client.misk

import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import misk.hibernate.Session
import misk.hibernate.Transacter

/**
 * Provides connectivity to a singleton database or a set of database shards.
 */
interface InstanceProvider {
  /**
   * Names the databases that will be connected with [transaction]. In a Vitess environment these
   * are the shard names.
   */
  fun names(request: PrepareBackfillRequest): List<String>

  fun <T> transaction(instanceName: String, task: (Session) -> T): T
}

/**
 * Implements a simple unsharded Hibernate backfill that creates a single Backfila instance. If you
 * are using a Vitess datasource you should almost certainly be using one of the Vitess base
 * backfills. [VitessSingleCursorBackfill] [VitessShardedBackfill]
 */
class UnshardedInstanceProvider(val transacter: Transacter) : InstanceProvider {

  override fun names(request: PrepareBackfillRequest) = listOf("only")

  override fun <T> transaction(instanceName: String, task: (Session) -> T) =
      transacter.transaction(task)
}
