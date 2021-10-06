package app.cash.backfila.client.misk.dynamodb

import app.cash.backfila.client.misk.dynamodb.internal.AwsAttributeValueAdapter
import app.cash.backfila.client.misk.dynamodb.internal.DynamoDbBackend
import app.cash.backfila.client.spi.BackfillBackend
import com.google.inject.Binder
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import misk.inject.KAbstractModule
import javax.inject.Qualifier

class DynamoDbBackfillModule<T : DynamoDbBackfill<*, *>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    install(DynamoDbBackfillBackendModule)
    // Ensures that the backfill class is injectable. If you are failing this check you probably
    // want to add an @Inject annotation to your class or check that all of your dependencies are provided.
    binder().getProvider(backfillClass.java)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : DynamoDbBackfill<*, *>> create(): DynamoDbBackfillModule<T> = create(T::class)

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

  /** The DynamoDb Backend needs a modified Moshi */
  @Provides @Singleton @ForDynamoDbBackend
  fun provideDynamoMoshi(): Moshi {
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
  ForDynamoDbBackend::class.java
)

/** Annotation for specifying dependencies specifically for this Backend. */
@Qualifier annotation class ForDynamoDbBackend
