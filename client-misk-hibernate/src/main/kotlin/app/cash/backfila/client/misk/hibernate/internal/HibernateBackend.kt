package app.cash.backfila.client.misk.hibernate.internal

import app.cash.backfila.client.Description
import app.cash.backfila.client.DeleteBy
import app.cash.backfila.client.misk.hibernate.ForHibernateBackend
import app.cash.backfila.client.parseDeleteByDate
import app.cash.backfila.client.misk.hibernate.HibernateBackfill
import app.cash.backfila.client.misk.hibernate.PkeySqlAdapter
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
import misk.hibernate.DbEntity
import misk.hibernate.Id
import misk.hibernate.Query
import kotlin.reflect.full.findAnnotation

@Singleton
class HibernateBackend @Inject constructor(
  private val injector: Injector,
  @ForHibernateBackend private val backfills: MutableMap<String, KClass<out HibernateBackfill<*, *, *>>>,
  internal var pkeySqlAdapter: PkeySqlAdapter,
  internal var queryFactory: Query.Factory
) : BackfillBackend {

  /** Creates Backfill instances. Each backfill ID gets a new Backfill instance. */
  private fun getBackfill(name: String, backfillId: String): HibernateBackfill<*, *, *>? {
    val backfillClass = backfills[name]
    return if (backfillClass != null) {
      injector.getInstance(backfillClass.java) as HibernateBackfill<*, *, *>
    } else {
      null
    }
  }

  private fun <E : DbEntity<E>, Pkey : Any, Param : Any> createHibernateOperator(
    backfill: HibernateBackfill<E, Pkey, Param>
  ) = HibernateBackfillOperator(
    backfill,
    BackfilaParametersOperator(parametersClass(backfill::class)),
    this
  )

  override fun create(backfillName: String, backfillId: String): BackfillOperator? {
    val backfill = getBackfill(backfillName, backfillId)

    if (backfill != null) {
      @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
      return createHibernateOperator(backfill as HibernateBackfill<DbPlaceholder, Any, Any>)
    }

    return null
  }

  override fun backfills(): Set<BackfillRegistration> {
    return backfills.map {
      BackfillRegistration(
        name = it.key,
        description = it.value.findAnnotation<Description>()?.text,
        parametersClass = parametersClass(it.value as KClass<HibernateBackfill<*, *, Any>>),
        deleteBy = it.value.findAnnotation<DeleteBy>()?.parseDeleteByDate(),
      )
    }.toSet()
  }

  private fun <T : Any> parametersClass(backfillClass: KClass<out HibernateBackfill<*, *, T>>): KClass<T> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(backfillClass.java)

    // Like Backfill<E, Id<E>, MyDataClass>.
    val supertype = thisType.getSupertype(HibernateBackfill::class.java).type as ParameterizedType

    // Like MyDataClass
    return (Types.getRawType(supertype.actualTypeArguments[2]) as Class<T>).kotlin
  }

  /** This placeholder exists so we can create a backfill without a type parameter. */
  private class DbPlaceholder : DbEntity<DbPlaceholder> {
    override val id: Id<DbPlaceholder> get() = throw IllegalStateException("unreachable")
  }
}
