package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.hibernate.internal.BoundingRangeStrategy
import app.cash.backfila.client.misk.hibernate.internal.UnshardedHibernateBoundingRangeStrategy
import app.cash.backfila.client.misk.hibernate.internal.VitessShardedBoundingRangeStrategy
import app.cash.backfila.client.misk.hibernate.internal.VitessSingleCursorBoundingRangeStrategy
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import javax.persistence.Table
import misk.hibernate.DbEntity
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.shards
import misk.hibernate.transaction
import misk.vitess.Keyspace
import misk.vitess.Shard

/**
 * Provides connectivity to a singleton database or a set of database shards.
 */
interface PartitionProvider {
  /**
   * Names the databases that will be connected with [transaction]. In a Vitess environment these
   * are the shard names.
   */
  fun names(request: PrepareBackfillRequest): List<String>

  fun <T> transaction(partitionName: String, task: (Session) -> T): T

  fun <E : DbEntity<E>, Pkey : Any> boundingRangeStrategy(): BoundingRangeStrategy<E, Pkey>
}

/**
 * A simple unsharded partition provider that uses a single Backfila partition. If you
 * are using a Vitess datasource you should almost certainly be using one of the Vitess partition
 * providers. [VitessShardedPartitionProvider] [VitessSingleCursorPartitionProvider]
 */
class UnshardedPartitionProvider(private val transacter: Transacter) : PartitionProvider {
  override fun names(request: PrepareBackfillRequest) = listOf("only")

  override fun <T> transaction(partitionName: String, task: (Session) -> T) =
      transacter.transaction(task)

  override fun <E : DbEntity<E>, Pkey : Any> boundingRangeStrategy(): BoundingRangeStrategy<E, Pkey> {
    return UnshardedHibernateBoundingRangeStrategy(this)
  }
}

/**
 * A sharded partition provider that creates a backfila partition per Vitess shard. Since a cursor is
 * maintained for each shard separately, if entities are moved between shards it's possible they
 * will be missed by the backfill. Also at minimum one thread is used per shard since each shard
 * is its own partition. If your entities can move, or if you need to run this backfill slower
 * than one thread per shard, consider using [VitessSingleCursorPartitionProvider] instead.
 */
class VitessShardedPartitionProvider<E : DbEntity<E>, Pkey : Any>(
  private val transacter: Transacter,
  backfill: HibernateBackfill<E, Pkey, *>
) : PartitionProvider {
  private val keyspace = Keyspace(backfill.entityClass.java.getAnnotation(Table::class.java).schema)

  override fun names(request: PrepareBackfillRequest) = shards().map { it.name }

  override fun <T> transaction(partitionName: String, task: (Session) -> T) =
      transacter.transaction(Shard(keyspace, partitionName), task)

  override fun <E : DbEntity<E>, Pkey : Any> boundingRangeStrategy(): BoundingRangeStrategy<E, Pkey> {
    return VitessShardedBoundingRangeStrategy(this)
  }

  private fun shards() = transacter.shards(keyspace)
}

/**
 * A partition provider that iterates over sharded vitess using a single cursor. This can only be
 * used if pkeys are unique across all shards, e.g. using a vitess sequence.
 *
 * Prefer [VitessShardedPartitionProvider]
 *
 * The benefits of this vs [VitessShardedPartitionProvider] is that if entities move from one
 * customer to another, they will not be missed because one shard's cursor cannot get ahead of
 * another's. It is also indifferent to shard splits and can run slower than one thread per shard.
 * The disadvantage is less efficient concurrency, since batches are computed by scanning all shards
 * each time, rather than splitting the work by shard.
 */
class VitessSingleCursorPartitionProvider<E : DbEntity<E>, Pkey : Any>(
  private val transacter: Transacter,
  backfill: HibernateBackfill<E, Pkey, *>
) : PartitionProvider {
  private val keyspace = Keyspace(backfill.entityClass.java.getAnnotation(Table::class.java).schema)

  override fun names(request: PrepareBackfillRequest) = listOf("only")

  override fun <T> transaction(partitionName: String, task: (Session) -> T) =
      transacter.transaction(task)

  override fun <E : DbEntity<E>, Pkey : Any> boundingRangeStrategy(): BoundingRangeStrategy<E, Pkey> {
    return VitessSingleCursorBoundingRangeStrategy<E, Pkey>(transacter, keyspace)
  }
}
