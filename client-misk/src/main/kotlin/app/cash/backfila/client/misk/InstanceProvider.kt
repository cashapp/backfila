package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.internal.BoundingRangeStrategy
import app.cash.backfila.client.misk.internal.UnshardedHibernateBoundingRangeStrategy
import app.cash.backfila.client.misk.internal.VitessShardedBoundingRangeStrategy
import app.cash.backfila.client.misk.internal.VitessSingleCursorBoundingRangeStrategy
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import misk.hibernate.Check.FULL_SCATTER
import misk.hibernate.DbEntity
import misk.hibernate.Keyspace
import misk.hibernate.Session
import misk.hibernate.Shard
import misk.hibernate.Transacter
import misk.hibernate.annotation.Keyspace as KeyspaceAnnotation
import misk.hibernate.shards
import misk.hibernate.transaction

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

  fun <E : DbEntity<E>, Pkey : Any> boundingRangeStrategy(): BoundingRangeStrategy<E, Pkey>
}

/**
 * A simple unsharded instance provider that uses a single Backfila instance. If you
 * are using a Vitess datasource you should almost certainly be using one of the Vitess instance
 * providers. [VitessShardedInstanceProvider] [VitessSingleCursorInstanceProvider]
 */
class UnshardedInstanceProvider(val transacter: Transacter) : InstanceProvider {

  override fun names(request: PrepareBackfillRequest) = listOf("only")

  override fun <T> transaction(instanceName: String, task: (Session) -> T) =
      transacter.transaction(task)

  override fun <E : DbEntity<E>, Pkey : Any> boundingRangeStrategy(): BoundingRangeStrategy<E, Pkey> {
    return UnshardedHibernateBoundingRangeStrategy(this)
  }
}

/**
 * A sharded instance provider that creates a backfila instance per Vitess shard. Since a cursor is
 * maintained for each shard separately, if entities are moved between shards it's possible they
 * will be missed by the backfill. Also at minimum one thread is used per shard since each shard
 * is its own instance. If your entities can move, or if you need to run this backfill slower
 * than one thread per shard, consider using [VitessSingleCursorInstanceProvider] instead.
 */
class VitessShardedInstanceProvider<E : DbEntity<E>, Pkey : Any>(val transacter: Transacter, val backfill: Backfill<E, Pkey>) : InstanceProvider {
  private val keyspace = Keyspace(backfill.entityClass.java.getAnnotation(KeyspaceAnnotation::class.java).value)

  override fun names(request: PrepareBackfillRequest) = shards().map { it.name }

  override fun <T> transaction(instanceName: String, task: (Session) -> T) =
      transacter.transaction(Shard(keyspace, instanceName), task)

  override fun <E : DbEntity<E>, Pkey : Any> boundingRangeStrategy(): BoundingRangeStrategy<E, Pkey> {
    return VitessShardedBoundingRangeStrategy(this)
  }

  private fun shards() = transacter.shards(keyspace)
}

/**
 * A instance provider that iterates over sharded vitess using a single cursor. This can only be
 * used if pkeys are unique across all shards, e.g. using a vitess sequence.
 *
 * THIS USES FULL SCATTER CROSS SHARD VITESS QUERIES! Prefer [VitessShardedInstanceProvider]
 *
 * The benefits of this vs [VitessShardedInstanceProvider] is that if entities move from one
 * customer to another, they will not be missed because one shard's cursor cannot get ahead of
 * another's. It is also indifferent to shard splits and can run slower than one thread per shard.
 * The disadvantage is less efficient concurrency and cross shard queries, since batches are
 * computed by scanning all shards each time, rather than splitting the work by shard.
 */
class VitessSingleCursorInstanceProvider<E : DbEntity<E>, Pkey : Any>(val transacter: Transacter, val backfill: Backfill<E, Pkey>) : InstanceProvider {
  private val keyspace = Keyspace(backfill.entityClass.java.getAnnotation(KeyspaceAnnotation::class.java).value)

  override fun names(request: PrepareBackfillRequest) = listOf("only")

  override fun <T> transaction(instanceName: String, task: (Session) -> T) =
      transacter.transaction { session -> session.disableChecks(listOf(FULL_SCATTER)) { task(session) } }

  override fun <E : DbEntity<E>, Pkey : Any> boundingRangeStrategy(): BoundingRangeStrategy<E, Pkey> {
    return VitessSingleCursorBoundingRangeStrategy<E, Pkey>(transacter, keyspace)
  }
}
