package app.cash.backfila.client.dynamodb

import app.cash.backfila.client.BackfillConfig
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper

/**
 * A base class that may make it easier to mutate the items in the DynamoDB store.
 *
 * If saving fails the batch will fail. If succeeded rows are part of a failed batch they will be
 * retried so implementations must be idempotent.
 */
abstract class UpdateInPlaceDynamoDbBackfill<I : Any, P : Any>(
  val dynamoDb: DynamoDBMapper
) : DynamoDbBackfill<I, P>() {
  override fun runBatch(items: List<I>, config: BackfillConfig<P>) {
    val itemsToSave = mutableListOf<I>()
    for (item in items) {
      val saveItem = runOne(item, config)
      if (saveItem) {
        itemsToSave += item
      }
    }
    if (itemsToSave.isNotEmpty()) {
      val failedBatch = dynamoDb.batchSave(itemsToSave)
      require(failedBatch.isEmpty()) {
        "failed to save items: $failedBatch"
      }
    }
  }

  /**
   * Called for each matching record. Returns true to save the item after returning; false to not
   * save the item.
   */
  abstract fun runOne(item: I, config: BackfillConfig<P>): Boolean
}
