package app.cash.backfila.client.s3

import app.cash.backfila.client.s3.internal.S3DatasourceBackend
import app.cash.backfila.client.spi.BackfillBackend
import com.google.inject.AbstractModule
import com.google.inject.Binder
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import javax.inject.Qualifier
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Installs the [BackfillBackend] for S3 Datasource backfills. See the java doc for [RealBackfillModule].
 */
class S3DatasourceBackfillModule<T : S3DatasourceBackfill<*, *>> private constructor(
  private val backfillClass: KClass<T>,
) : AbstractModule() {
  override fun configure() {
    install(S3DatasourceBackfillBackendModule)
    // Ensures that the backfill class is injectable. If you are failing this check you probably
    // want to add an @Inject annotation to your class or check that all of your dependencies are provided.
    binder().getProvider(backfillClass.java)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : S3DatasourceBackfill<*, *>> create(): S3DatasourceBackfillModule<T> = create(T::class)

    @JvmStatic
    fun <T : S3DatasourceBackfill<*, *>> create(backfillClass: KClass<T>): S3DatasourceBackfillModule<T> {
      return S3DatasourceBackfillModule(backfillClass)
    }

    @JvmStatic
    fun <T : S3DatasourceBackfill<*, *>> create(backfillClass: Class<T>): S3DatasourceBackfillModule<T> {
      return S3DatasourceBackfillModule(backfillClass.kotlin)
    }
  }
}

/**
 * This is a kotlin object so these dependencies are only installed once.
 */
private object S3DatasourceBackfillBackendModule : AbstractModule() {
  override fun configure() {
    Multibinder.newSetBinder(binder(), BackfillBackend::class.java).addBinding()
      .to(S3DatasourceBackend::class.java)
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out S3DatasourceBackfill<*, *>>>() {},
  ForS3Backend::class.java,
)

/** Annotation for specifying dependencies specifically for this Backend. */
@Qualifier annotation class ForS3Backend
