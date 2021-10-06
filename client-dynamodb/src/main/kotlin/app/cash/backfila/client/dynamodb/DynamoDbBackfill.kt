package app.cash.backfila.client.dynamodb

import app.cash.backfila.client.Backfill
import app.cash.backfila.client.BackfillConfig
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass

abstract class DynamoDbBackfill<I : Any, P : Any> : Backfill {
  val itemType: KClass<I>

  /*
   * Extract the type parameters from the subtype's generic declaration. This uses Guice magic to
   * read the ("I") type parameter.
   */
  init {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(this::class.java)

    // Like Backfill<MyItem, Parameters>.
    val supertype = thisType.getSupertype(
      DynamoDbBackfill::class.java
    ).type as ParameterizedType

    // Like MyItem.
    @Suppress("UNCHECKED_CAST")
    itemType = (Types.getRawType(supertype.actualTypeArguments[0]) as Class<I>).kotlin
  }

  /**
   * Override this and throw an exception to prevent the backfill from being created.
   * This is also a good place to do any prep work before batches are run.
   */
  open fun validate(config: BackfillConfig<P>) {}

  /**
   * Called for each batch of matching records.
   * Override in a backfill to process all records in a batch.
   */
  abstract fun runBatch(items: List<I>, config: BackfillConfig<P>)

  /**
   * Override this to force Backfila to run this number of batches in total, divided among the
   * partitions.
   *
   * If null, Backfila will use a dynamic segment count. This automatically guesses the segment
   * count to fit the requested batch size. Override this if the guess is bad, such as when your
   * data is not uniformly distributed.
   */
  open fun fixedSegmentCount(config: BackfillConfig<P>): Int? = null

  /**
   * The number of independent workers to perform the backfill. When the Backfill is executing, each
   * worker runs 1 or more batches concurrently. Set a low number here to reduce the total tracking
   * overhead in Backfila; set a higher number for more concurrency. The default of 8 means that
   * the Backfill will run at least 8 batches concurrently.
   */
  open fun partitionCount(config: BackfillConfig<P>): Int = 8

  /**
   * It is rather easy to run a backfill against a dynamo instance that is configured expensively.
   * Update dynamo so the billing mode is PROVISIONED rather than PAY_PER_REQUEST as the latter can
   * be very expensive.
   */
  open fun mustHaveProvisionedBillingMode(): Boolean = true

  /** See [ScanRequest.setFilterExpression]. */
  open fun filterExpression(config: BackfillConfig<P>): String? = null

  /** See [ScanRequest.setExpressionAttributeValues]. */
  open fun expressionAttributeValues(config: BackfillConfig<P>): Map<String, AttributeValue>? = null
}
