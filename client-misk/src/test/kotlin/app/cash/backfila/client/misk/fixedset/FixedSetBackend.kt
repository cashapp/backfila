package app.cash.backfila.client.misk.fixedset

import app.cash.backfila.client.misk.Description
import app.cash.backfila.client.misk.ForBackfila
import app.cash.backfila.client.misk.spi.BackfilaParametersOperator
import app.cash.backfila.client.misk.spi.BackfillBackend
import app.cash.backfila.client.misk.spi.BackfillOperator
import app.cash.backfila.client.misk.spi.BackfillRegistration
import com.google.inject.Injector
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class FixedSetBackend @Inject constructor(
  private val injector: Injector,
  @ForBackfila private val backfills: MutableMap<String, KClass<out FixedSetBackfill<*>>>,
  private val datastore: FixedSetDatastore
) : BackfillBackend {

  /** Creates Backfill instances. Each backfill ID gets a new Backfill instance. */
  private fun getBackfill(name: String): FixedSetBackfill<*>? {
    val backfillClass = backfills[name]
    return if (backfillClass != null) {
      injector.getInstance(backfillClass.java) as FixedSetBackfill
    } else {
      null
    }
  }

  private fun <Param : Any> createOperator(
    backfill: FixedSetBackfill<Param>
  ) = FixedSetBackfillOperator(
    backfill = backfill,
    datastore = datastore,
    parametersOperator = BackfilaParametersOperator(parametersClass(backfill::class))
  )

  override fun create(backfillName: String, backfillId: String): BackfillOperator? {
    val backfill = getBackfill(backfillName)

    if (backfill != null) {
      @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
      return createOperator(backfill as FixedSetBackfill<Any>)
    }

    return null
  }

  override fun backfills(): Set<BackfillRegistration> {
    return backfills.map {
      BackfillRegistration(
        name = it.key,
        description = (it.value.annotations.find { it is Description } as? Description)?.text,
        parametersClass = parametersClass(it.value as KClass<FixedSetBackfill<Any>>)
      )
    }.toSet()
  }

  private fun <T : Any> parametersClass(backfillClass: KClass<out FixedSetBackfill<T>>): KClass<T> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(backfillClass.java)

    // Like Backfill<MyDataClass>.
    val supertype = thisType.getSupertype(FixedSetBackfill::class.java).type as ParameterizedType

    // Like MyDataClass
    return (Types.getRawType(supertype.actualTypeArguments[0]) as Class<T>).kotlin
  }
}
