package app.cash.backfila.client.misk.dynamodbv2

import app.cash.backfila.client.misk.dynamodbv2.internal.AwsAttributeValueAdapter
import app.cash.backfila.client.misk.dynamodbv2.internal.DynamoDbBackend
import app.cash.backfila.client.spi.BackfillBackend
import com.google.inject.Binder
import com.google.inject.BindingAnnotation
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.inject.KAbstractModule
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class DynamoDbBackfillModule<T : DynamoDbBackfill<*, *>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    install(DynamoDbBackfillBackendModule)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : DynamoDbBackfill<*, *>> create(): DynamoDbBackfillModule<T> =
      create(T::class)

    @JvmStatic
    fun <T : DynamoDbBackfill<*, *>> create(backfillClass: KClass<T>): DynamoDbBackfillModule<T> {
      return DynamoDbBackfillModule(backfillClass)
    }
  }
}

private object DynamoDbBackfillBackendModule : KAbstractModule() {
  override fun configure() {
    multibind<BackfillBackend>().to<DynamoDbBackend>()
  }

  @Provides
  @Singleton
  @ForBackfila
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
