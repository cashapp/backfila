package app.cash.backfila.client.misk.dynamodb.internal

import app.cash.backfila.client.misk.Description
import app.cash.backfila.client.misk.dynamodb.DynamoDbBackfill
import app.cash.backfila.client.misk.dynamodb.ForBackfila
import app.cash.backfila.client.misk.spi.BackfilaParametersOperator
import app.cash.backfila.client.misk.spi.BackfillBackend
import app.cash.backfila.client.misk.spi.BackfillOperator
import app.cash.backfila.client.misk.spi.BackfillRegistration
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.google.inject.Injector
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class DynamoDbBackend @Inject constructor(
  private val injector: Injector,
  @ForBackfila private val backfills: MutableMap<String, KClass<out DynamoDbBackfill<*, *>>>,
  val dynamoDb: DynamoDBMapper
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
      dynamoDb,
      backfill,
      BackfilaParametersOperator(parametersClass(backfill::class))
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
          description = (it.value.annotations.find { it is Description } as? Description)?.text,
          parametersClass = parametersClass(it.value as KClass<DynamoDbBackfill<Any, Any>>)
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
