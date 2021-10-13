package app.cash.backfila.client.static

import app.cash.backfila.client.Backfill
import app.cash.backfila.client.BackfillConfig
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

abstract class StaticDatasourceBackfill<I : Any, P : Any> : Backfill {
  val itemType: KClass<I>

  /*
   * Extract the type parameters from the subtype's generic declaration. This uses Guice magic to
   * read the ("I") type parameter.
   */
  init {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(this::class.java)

    // Like Backfill<MyItem, Parameters>.
    val supertype = thisType.getSupertype(
      StaticDatasourceBackfill::class.java
    ).type as ParameterizedType

    // Like MyItem.
    @Suppress("UNCHECKED_CAST")
    itemType = (Types.getRawType(supertype.actualTypeArguments[0]) as Class<I>).kotlin
  }

  /**
   * Override this and throw an exception to prevent the backfill from being created.
   * This is also a good place to do any prep work before batches are run.
   */
  open fun validate(config: BackfillConfig<P>) {}

  /**
   * Called for each batch of matching records.
   * Override in a backfill to process all records in a batch.
   */
  open fun runBatch(items: List<I>, config: BackfillConfig<P>) {
    items.forEach { runOne(it, config) }
  }

  /**
   * Called for each matching record.
   * Override in a backfill to process one record at a time.
   */
  open fun runOne(item: I, config: BackfillConfig<P>) {
  }

  /**
   * This invokes the static list of items that the backfill will iterate over.
   */
  abstract val staticDatasource: List<I>
}
