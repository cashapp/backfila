package app.cash.backfila.client.misk.spanner

import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.misk.spanner.internal.SpannerBackend
import com.google.inject.Binder
import com.google.inject.Provides
import com.google.inject.Singleton
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Qualifier
import kotlin.reflect.KClass
import misk.inject.KAbstractModule
import kotlin.reflect.jvm.jvmName

class SpannerBackfillModule<T : SpannerBackfill<*>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    install(SpannerBackfillBackendModule)
    // Ensures that the backfill class is injectable. If you are failing this check you probably
    // want to add an @Inject annotation to your class or check that all of your dependencies are provided.
    binder().getProvider(backfillClass.java)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : SpannerBackfill<*>> create(): SpannerBackfillModule<T> =
      create(T::class)

    @JvmStatic
    fun <T : SpannerBackfill<*>> create(backfillClass: KClass<T>): SpannerBackfillModule<T> {
      return SpannerBackfillModule(backfillClass)
    }

    @JvmStatic
    fun <T : SpannerBackfill<*>> create(backfillClass: Class<T>): SpannerBackfillModule<T> {
      return SpannerBackfillModule(backfillClass.kotlin)
    }
  }
}

/**
 * This is a kotlin object so these dependencies are only installed once.
 */
private object SpannerBackfillBackendModule : KAbstractModule() {
  override fun configure() {
    Multibinder.newSetBinder(binder(), BackfillBackend::class.java).addBinding()
      .to(SpannerBackend::class.java)
  }

  @Provides @Singleton @ForSpannerBackend
  fun provideSpannerMoshi(): Moshi {
    return Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out SpannerBackfill<*>>>() {},
  ForSpannerBackend::class.java
)

/** Annotation for specifying dependencies specifically for this Backend. */
@Qualifier annotation class ForSpannerBackend
