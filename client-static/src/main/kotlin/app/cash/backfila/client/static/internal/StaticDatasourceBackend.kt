package app.cash.backfila.client.static.internal

import app.cash.backfila.client.Description
import app.cash.backfila.client.DeleteBy
import app.cash.backfila.client.parseDeleteByDate
import app.cash.backfila.client.static.ForStaticBackend
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.spi.BackfillRegistration
import app.cash.backfila.client.static.StaticDatasourceBackfill
import com.google.inject.Injector
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

@Singleton
class StaticDatasourceBackend @Inject constructor(
  private val injector: Injector,
  @ForStaticBackend private val backfills: MutableMap<String, KClass<out StaticDatasourceBackfill<*, *>>>
) : BackfillBackend {

  /** Creates Backfill instances. Each backfill ID gets a new Backfill instance. */
  private fun getBackfill(name: String): StaticDatasourceBackfill<*, *>? {
    val backfillClass = backfills[name]
    return if (backfillClass != null) {
      injector.getInstance(backfillClass.java) as StaticDatasourceBackfill<*, *>
    } else {
      null
    }
  }

  private fun <E : Any, Param : Any> createStaticDatasourceOperator(
    backfill: StaticDatasourceBackfill<E, Param>
  ) = StaticDatasourceBackfillOperator(
    backfill,
    BackfilaParametersOperator(parametersClass(backfill::class))
  )

  override fun create(backfillName: String, backfillId: String): BackfillOperator? {
    val backfill = getBackfill(backfillName)

    if (backfill != null) {
      @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
      return createStaticDatasourceOperator(backfill as StaticDatasourceBackfill<Any, Any>)
    }

    return null
  }

  override fun backfills(): Set<BackfillRegistration> {
    return backfills.map {
      BackfillRegistration(
        name = it.key,
        description = it.value.findAnnotation<Description>()?.text,
        parametersClass = parametersClass(it.value as KClass<StaticDatasourceBackfill<Any, Any>>),
        deleteBy = it.value.findAnnotation<DeleteBy>()?.parseDeleteByDate(),
      )
    }.toSet()
  }

  private fun <P : Any> parametersClass(backfillClass: KClass<out StaticDatasourceBackfill<*, P>>): KClass<P> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(backfillClass.java)

    // Like StaticDatasourceBackfill<MyItemClass, MyParameterClass>.
    val supertype = thisType.getSupertype(StaticDatasourceBackfill::class.java).type as ParameterizedType

    // Like MyParameterClass
    return (Types.getRawType(supertype.actualTypeArguments[1]) as Class<P>).kotlin
  }
}
