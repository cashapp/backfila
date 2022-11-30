package app.cash.backfila.client.s3.internal

import app.cash.backfila.client.DeleteBy
import app.cash.backfila.client.Description
import app.cash.backfila.client.parseDeleteByDate
import app.cash.backfila.client.s3.ForS3Backend
import app.cash.backfila.client.s3.S3DatasourceBackfill
import app.cash.backfila.client.s3.shim.S3Service
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
class S3DatasourceBackend @Inject constructor(
  private val injector: Injector,
  @ForS3Backend private val backfills: MutableMap<String, KClass<out S3DatasourceBackfill<*, *>>>,
  private val s3Service: S3Service,
) : BackfillBackend {

  /** Creates Backfill instances. Each backfill ID gets a new Backfill instance. */
  private fun getBackfill(name: String): S3DatasourceBackfill<*, *>? {
    val backfillClass = backfills[name]
    return backfillClass?.let {
      injector.getInstance(backfillClass.java) as S3DatasourceBackfill<*, *>
    }
  }

  private fun <R : Any, Param : Any> createS3DatasourceOperator(
    backfill: S3DatasourceBackfill<R, Param>,
  ) = S3DatasourceBackfillOperator(
    backfill,
    BackfilaParametersOperator(parametersClass(backfill::class)),
    s3Service,
  )

  override fun create(backfillName: String): BackfillOperator? {
    return getBackfill(backfillName)?.let { backfill ->
      @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
      return createS3DatasourceOperator(backfill as S3DatasourceBackfill<Any, Any>)
    }
  }

  override fun backfills(): Set<BackfillRegistration> {
    return backfills.map {
      BackfillRegistration(
        name = it.key,
        description = it.value.findAnnotation<Description>()?.text,
        parametersClass = parametersClass(it.value as KClass<S3DatasourceBackfill<Any, Any>>),
        deleteBy = it.value.findAnnotation<DeleteBy>()?.parseDeleteByDate(),
      )
    }.toSet()
  }

  private fun <R : Any, P : Any> parametersClass(backfillClass: KClass<out S3DatasourceBackfill<R, P>>): KClass<P> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(backfillClass.java)

    // Like S3DatasourceBackfill<MyParameterClass>.
    val supertype = thisType.getSupertype(S3DatasourceBackfill::class.java).type as ParameterizedType

    // Like MyParameterClass
    return (Types.getRawType(supertype.actualTypeArguments[1]) as Class<P>).kotlin
  }
}
