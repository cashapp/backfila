package app.cash.backfila.client.s3

import app.cash.backfila.client.s3.internal.S3DatasourceBackendV2
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
 * Installs the [BackfillBackend] for S3 Datasource backfills using AWS SDK V2. See the java doc for [RealBackfillModule].
 */
class S3DatasourceBackfillModuleV2<T : S3DatasourceBackfill<*, *>> private constructor(
  private val backfillClass: KClass<T>,
) : AbstractModule() {
  override fun configure() {
    install(S3DatasourceBackfillBackendModuleV2)
    // Ensures that the backfill class is injectable. If you are failing this check you probably
    // want to add an @Inject annotation to your class or check that all of your dependencies are provided.
    binder().getProvider(backfillClass.java)
    mapBinderV2(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : S3DatasourceBackfill<*, *>> create(): S3DatasourceBackfillModuleV2<T> = create(T::class)

    @JvmStatic
    fun <T : S3DatasourceBackfill<*, *>> create(backfillClass: KClass<T>): S3DatasourceBackfillModuleV2<T> {
      return S3DatasourceBackfillModuleV2(backfillClass)
    }

    @JvmStatic
    fun <T : S3DatasourceBackfill<*, *>> create(backfillClass: Class<T>): S3DatasourceBackfillModuleV2<T> {
      return S3DatasourceBackfillModuleV2(backfillClass.kotlin)
    }
  }
}

/**
 * This is a kotlin object so these dependencies are only installed once.
 */
private object S3DatasourceBackfillBackendModuleV2 : AbstractModule() {
  override fun configure() {
    Multibinder.newSetBinder(binder(), BackfillBackend::class.java).addBinding()
      .to(S3DatasourceBackendV2::class.java)
  }
}

private fun mapBinderV2(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out S3DatasourceBackfill<*, *>>>() {},
  ForS3BackendV2::class.java,
)

/** Annotation for specifying dependencies specifically for AWS SDK V2 Backend. */
@Qualifier annotation class ForS3BackendV2
