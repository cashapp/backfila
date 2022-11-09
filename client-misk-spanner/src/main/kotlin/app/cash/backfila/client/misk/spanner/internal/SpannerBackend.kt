package app.cash.backfila.client.misk.spanner.internal

import app.cash.backfila.client.DeleteBy
import app.cash.backfila.client.Description
import app.cash.backfila.client.misk.spanner.ForSpannerBackend
import app.cash.backfila.client.misk.spanner.SpannerBackfill
import app.cash.backfila.client.parseDeleteByDate
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillRegistration
import com.google.cloud.spanner.Spanner
import com.google.inject.Injector
import com.google.inject.TypeLiteral
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

@Singleton
internal class SpannerBackend @Inject constructor(
  private val injector: Injector,
  @ForSpannerBackend private val backfills: MutableMap<String, KClass<out SpannerBackfill<*>>>,
  @ForSpannerBackend internal val moshi: Moshi,
  internal val spanner: Spanner,
) : BackfillBackend {

  private fun getBackfill(name: String): SpannerBackfill<*>? {
    val backfillClass = backfills[name] ?: return null
    return injector.getInstance(backfillClass.java) as SpannerBackfill<*>
  }

  private fun <Param : Any> createSpannerOperator(
    backfill: SpannerBackfill<Param>
  ) = SpannerBackfillOperator(
    backfill,
    BackfilaParametersOperator(parametersClass(backfill::class)),
    this,
  )

  override fun create(backfillName: String, backfillId: String): BackfillOperator? {
    val backfill = getBackfill(backfillName)

    if (backfill != null) {
      @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
      return createSpannerOperator(backfill as SpannerBackfill<Any>)
    }

    return null
  }

  override fun backfills(): Set<BackfillRegistration> {
    return backfills.map {
      BackfillRegistration(
        name = it.key,
        description = it.value.findAnnotation<Description>()?.text,
        parametersClass = parametersClass(it.value as KClass<SpannerBackfill<Any>>),
        deleteBy = it.value.findAnnotation<DeleteBy>()?.parseDeleteByDate(),
      )
    }.toSet()
  }

  private fun <T : Any> parametersClass(backfillClass: KClass<out SpannerBackfill<T>>): KClass<T> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(backfillClass.java)

    // Like Backfill<MyDataClass>.
    val supertype = thisType.getSupertype(SpannerBackfill::class.java).type as ParameterizedType

    // Like MyDataClass
    return (Types.getRawType(supertype.actualTypeArguments[0]) as Class<T>).kotlin
  }
}
