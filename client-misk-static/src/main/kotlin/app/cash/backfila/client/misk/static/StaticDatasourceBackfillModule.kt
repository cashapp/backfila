package app.cash.backfila.client.misk.static

import app.cash.backfila.client.misk.static.internal.AwsAttributeValueAdapter
import app.cash.backfila.client.misk.static.internal.DynamoDbBackend
import app.cash.backfila.client.misk.spi.BackfillBackend
import com.google.inject.Binder
import com.google.inject.BindingAnnotation
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import misk.inject.KAbstractModule

class StaticDatasourceBackfillModule<T : StaticDatasourceBackfill<*, *>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    install(DynamoDbBackfillBackendModule)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : StaticDatasourceBackfill<*, *>> create(): StaticDatasourceBackfillModule<T> = create(T::class)

    @JvmStatic
    fun <T : StaticDatasourceBackfill<*, *>> create(backfillClass: KClass<T>): StaticDatasourceBackfillModule<T> {
      return StaticDatasourceBackfillModule(backfillClass)
    }
  }
}

private object DynamoDbBackfillBackendModule : KAbstractModule() {
  override fun configure() {
    multibind<BackfillBackend>().to<DynamoDbBackend>()
  }

  @Provides @Singleton @ForBackfila
  fun provideMoshi(): Moshi {
    return Moshi.Builder()
      .add(AwsAttributeValueAdapter)
      .add(KotlinJsonAdapterFactory()) // Must be last.
      .build()
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out DynamoDbBackfill<*, *>>>() {},
  ForBackfila::class.java
)

@BindingAnnotation
internal annotation class ForBackfila
