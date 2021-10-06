package app.cash.backfila.client.jooq.internal

import app.cash.backfila.client.Description
import app.cash.backfila.client.DeleteBy
import app.cash.backfila.client.parseDeleteByDate
import app.cash.backfila.client.jooq.ForJooqBackend
import app.cash.backfila.client.jooq.JooqBackfill
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillRegistration
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

@Singleton
class JooqBackend @Inject constructor(
  private val injector: Injector,
  @ForJooqBackend private val backfills: MutableMap<String, KClass<out JooqBackfill<*, *>>>
) : BackfillBackend {

  override fun create(backfillName: String, backfillId: String): JooqBackfillOperator<*, out Any>? {
    return getBackfill(backfillName)?.let {
      @Suppress("UNCHECKED_CAST")
      createJooqOperator(it as JooqBackfill<Any, Any>)
    }
  }

  override fun backfills(): Set<BackfillRegistration> {
    return backfills.map {
      BackfillRegistration(
        name = it.key,
        description = it.value.findAnnotation<Description>()?.text,
        parametersClass = parametersClass(it.value as KClass<JooqBackfill<*, Any>>),
        deleteBy = it.value.findAnnotation<DeleteBy>()?.parseDeleteByDate(),
      )
    }.toSet()
  }

  private fun <K : Any, Param : Any> createJooqOperator(
    backfill: JooqBackfill<K, Param>
  ) = JooqBackfillOperator(
    backfill,
    BackfilaParametersOperator(parametersClass(backfill::class)),
  )

  /** Creates Backfill instances. Each backfill ID gets a new Backfill instance. */
  private fun getBackfill(name: String): JooqBackfill<*, *>? {
    val backfillClass = backfills[name]
    return if (backfillClass != null) {
      injector.getInstance(backfillClass.java)
    } else {
      null
    }
  }

  private fun <T : Any> parametersClass(backfillClass: KClass<out JooqBackfill<*, T>>): KClass<T> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(backfillClass.java)

    // Like Backfill<Key, MyDataClass>.
    val supertype = thisType.getSupertype(JooqBackfill::class.java).type as ParameterizedType

    // Like MyDataClass
    return (Types.getRawType(supertype.actualTypeArguments[1]) as Class<T>).kotlin
  }
}
