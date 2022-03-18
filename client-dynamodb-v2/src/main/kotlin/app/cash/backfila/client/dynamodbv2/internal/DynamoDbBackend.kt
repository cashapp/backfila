package app.cash.backfila.client.dynamodbv2.internal

import app.cash.backfila.client.DeleteBy
import app.cash.backfila.client.Description
import app.cash.backfila.client.dynamodbv2.DynamoDbBackfill
import app.cash.backfila.client.dynamodbv2.ForDynamoDbBackend
import app.cash.backfila.client.parseDeleteByDate
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.spi.BackfillRegistration
import com.google.inject.Injector
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

@Singleton
class DynamoDbBackend @Inject constructor(
  private val injector: Injector,
  @ForDynamoDbBackend private val backfills: MutableMap<String, KClass<out DynamoDbBackfill<*, *>>>,
  private val dynamoDbClient: DynamoDbClient,
  val keyRangeCodec: DynamoDbKeyRangeCodec,
) : BackfillBackend {

  /** Creates Backfill instances. Each backfill ID gets a new Backfill instance. */
  private fun getBackfill(name: String): DynamoDbBackfill<*, *>? {
    val backfillClass = backfills[name]
    return if (backfillClass != null) {
      injector.getInstance(backfillClass.java) as DynamoDbBackfill<*, *>
    } else {
      null
    }
  }

  private fun <E : Any, Param : Any> createDynamoDbOperator(
    backfill: DynamoDbBackfill<E, Param>
  ) = DynamoDbBackfillOperator(
    dynamoDbClient,
    backfill,
    BackfilaParametersOperator(parametersClass(backfill::class)),
    keyRangeCodec
  )

  override fun create(backfillName: String, backfillId: String): BackfillOperator? {
    val backfill = getBackfill(backfillName)

    if (backfill != null) {
      @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
      return createDynamoDbOperator(backfill as DynamoDbBackfill<Any, Any>)
    }

    return null
  }

  override fun backfills(): Set<BackfillRegistration> {
    return backfills.map {
      BackfillRegistration(
        name = it.key,
        description = it.value.findAnnotation<Description>()?.text,
        parametersClass = parametersClass(it.value as KClass<DynamoDbBackfill<Any, Any>>),
        deleteBy = it.value.findAnnotation<DeleteBy>()?.parseDeleteByDate(),
      )
    }.toSet()
  }

  private fun <P : Any> parametersClass(backfillClass: KClass<out DynamoDbBackfill<*, P>>): KClass<P> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(backfillClass.java)

    // Like DynamoDbBackfill<MyItemClass, MyParameterClass>.
    val supertype = thisType.getSupertype(DynamoDbBackfill::class.java).type as ParameterizedType

    // Like MyParameterClass
    return (Types.getRawType(supertype.actualTypeArguments[1]) as Class<P>).kotlin
  }
}
