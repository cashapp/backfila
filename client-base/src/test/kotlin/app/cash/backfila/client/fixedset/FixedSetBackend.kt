package app.cash.backfila.client.fixedset

import app.cash.backfila.client.Description
import app.cash.backfila.client.DeleteBy
import app.cash.backfila.client.parseDeleteByDate
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.spi.BackfillRegistration
import com.google.inject.Injector
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

@Singleton
class FixedSetBackend @Inject constructor(
  private val injector: Injector,
  @ForFixedSetBackend private val backfills: MutableMap<String, KClass<out FixedSetBackfill<*>>>,
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
        description = it.value.findAnnotation<Description>()?.text,
        parametersClass = parametersClass(it.value as KClass<FixedSetBackfill<Any>>),
        deleteBy = it.value.findAnnotation<DeleteBy>()?.parseDeleteByDate(),
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
