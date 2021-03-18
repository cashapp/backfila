package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import misk.hibernate.DbEntity
import misk.hibernate.Query
import misk.inject.typeLiteral

/**
 * Implement this for each of your backfills. Install using [HibernateBackfillModule.create].
 */
abstract class HibernateBackfill<E : DbEntity<E>, Pkey : Any, Param : Any> : Backfill {
  val entityClass: KClass<E>
  val pkeyClass: KClass<Pkey>

  /*
   * Extract the type parameters from the subtype's generic declaration. This uses Guice magic to
   * read the first ("E") and second ("Pkey") type parameters.
   */
  init {
    // Like MyBackfill.
    val thisType = this::class.typeLiteral()

    // Like Backfill<MyEntity, Id<MyEntity>, Parameters>.
    val supertype = thisType.getSupertype(
        HibernateBackfill::class.java).type as ParameterizedType

    // Like MyEntity.
    @Suppress("UNCHECKED_CAST")
    entityClass = (Types.getRawType(supertype.actualTypeArguments[0]) as Class<E>).kotlin

    // Like Id.
    @Suppress("UNCHECKED_CAST")
    pkeyClass = (Types.getRawType(supertype.actualTypeArguments[1]) as Class<Pkey>).kotlin
  }

  /**
   * Returns an partition provider that is used for database connectivity.
   */
  abstract fun partitionProvider(): PartitionProvider

  /**
   * Criteria that filters which records are selected to backfill from the table.
   *
   * This must return a new instance of Query in every invocation.
   */
  abstract fun backfillCriteria(config: BackfillConfig<Param>): Query<E>

  /**
   * The name of the column that the backfill is keyed off of. Usually the primary key of the table.
   * Column must be unique and define an ordering.
   */
  open fun primaryKeyName(): String = "id"

  /**
   * The name of the hibernate property that the backfill is keyed off of.
   * Separate from primaryKeyName() as the casing is usually different.
   */
  open fun primaryKeyHibernateName(): String = "id"

  /**
   * Override this and throw an exception to prevent the backfill from being created.
   * This is also a good place to do any prep work before batches are run.
   */
  open fun validate(config: BackfillConfig<Param>) {}

  /**
   * Called for each batch of matching records.
   * Override in a backfill to process all records in a batch.
   */
  open fun runBatch(pkeys: List<Pkey>, config: BackfillConfig<Param>) {
    pkeys.forEach { runOne(it, config) }
  }

  /**
   * Called for each matching record.
   * Override in a backfill to process one record at a time.
   */
  open fun runOne(pkey: Pkey, config: BackfillConfig<Param>) {
  }
}
