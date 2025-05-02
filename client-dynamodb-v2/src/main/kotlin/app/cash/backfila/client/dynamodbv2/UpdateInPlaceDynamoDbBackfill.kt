package app.cash.backfila.client.dynamodbv2

import app.cash.backfila.client.BackfillConfig
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest
import software.amazon.awssdk.services.dynamodb.model.PutRequest
import software.amazon.awssdk.services.dynamodb.model.WriteRequest

/**
 * A base class that may make it easier to mutate the items in the DynamoDB store.
 *
 * If saving fails the batch will fail. If succeeded rows are part of a failed batch they will be
 * retried so implementations must be idempotent.
 */
abstract class UpdateInPlaceDynamoDbBackfill<I : Any, P : Any>(
  val dynamoDbClient: DynamoDbClient,

) : DynamoDbBackfill<I, P>() {
  companion object {
    // DynamoDB BatchWriteItem API has a limit of 25 items per request
    private const val BATCH_SIZE_LIMIT = 25
  }

  override fun runBatch(items: List<@JvmSuppressWildcards I>, config: BackfillConfig<P>) {
    val itemsToSave = mutableListOf<I>()
    for (item in items) {
      val saveItem = runOne(item, config)
      if (saveItem) {
        itemsToSave += item
      }
    }
    
    if (itemsToSave.isNotEmpty()) {
      // Process items in chunks of 25 or less
      itemsToSave.chunked(BATCH_SIZE_LIMIT).forEach { chunk ->
        val batchRequest = BatchWriteItemRequest.builder()
          .requestItems(
            mapOf(
              dynamoDbTable.tableName() to chunk.map {
                WriteRequest.builder().putRequest(
                  PutRequest.builder().item(
                    dynamoDbTable.tableSchema().itemToMap(it, true),
                  ).build(),
                ).build()
              },
            ),
          ).build()
        
        val failedBatch = dynamoDbClient.batchWriteItem(batchRequest)
        require(!failedBatch.hasUnprocessedItems() || !failedBatch.unprocessedItems().isNotEmpty()) {
          "failed to save items: $failedBatch"
        }
      }
    }
  }

  /**
   * Called for each matching record. Returns true to save the item after returning; false to not
   * save the item.
   */
  abstract fun runOne(item: I, config: BackfillConfig<P>): Boolean
}