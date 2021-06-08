package app.cash.backfila.client.misk.static.internal

import app.cash.backfila.client.misk.Description
import app.cash.backfila.client.misk.static.ForBackfila
import app.cash.backfila.client.misk.spi.BackfilaParametersOperator
import app.cash.backfila.client.misk.spi.BackfillBackend
import app.cash.backfila.client.misk.spi.BackfillOperator
import app.cash.backfila.client.misk.spi.BackfillRegistration
import app.cash.backfila.client.misk.static.StaticDatasourceBackfill
import com.google.inject.Injector
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class StaticDatasourceBackend @Inject constructor(
  private val injector: Injector,
  @ForBackfila private val backfills: MutableMap<String, KClass<out StaticDatasourceBackfill<*, *>>>
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
        description = (it.value.annotations.find { it is Description } as? Description)?.text,
        parametersClass = parametersClass(it.value as KClass<StaticDatasourceBackfill<Any, Any>>)
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
