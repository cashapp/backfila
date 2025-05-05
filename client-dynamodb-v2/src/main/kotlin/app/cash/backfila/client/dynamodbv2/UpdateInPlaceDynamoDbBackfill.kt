package app.cash.backfila.client.dynamodbv2

import app.cash.backfila.client.BackfillConfig
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse
import software.amazon.awssdk.services.dynamodb.model.PutRequest
import software.amazon.awssdk.services.dynamodb.model.WriteRequest
import kotlin.math.min
import kotlin.math.pow

/**
 * A base class that may make it easier to mutate the items in the DynamoDB store.
 *
 * Implements retry logic with exponential backoff for unprocessed items from BatchWriteItem operations.
 * Failed items from all batches are collected and retried together to optimize throughput.
 * The retry counter only starts when we stop making progress (no items processed in a round).
 * Implementations must be idempotent as items may be retried multiple times.
 */
abstract class UpdateInPlaceDynamoDbBackfill<I : Any, P : Any>(
  val dynamoDbClient: DynamoDbClient,
) : DynamoDbBackfill<I, P>() {
  companion object {
    // DynamoDB BatchWriteItem API has a limit of 25 items per request
    private const val BATCH_SIZE_LIMIT = 25
    
    // Retry configuration
    private const val MAX_RETRY_ATTEMPTS = 10
    private const val BASE_BACKOFF_MS = 50L
    private const val MAX_BACKOFF_MS = 5000L
  }

  override fun runBatch(items: List<@JvmSuppressWildcards I>, config: BackfillConfig<P>) {
    val itemsToSave = items.filter { runOne(it, config) }
    
    if (itemsToSave.isNotEmpty()) {
      var unprocessedItems = itemsToSave
      var stuckRetryCount = 0
      var totalAttempts = 0

      while (unprocessedItems.isNotEmpty()) {
        if (totalAttempts > 0) {
          // Calculate backoff time with exponential increase and jitter
          val backoffAttempts = stuckRetryCount.coerceAtLeast(1) // Use at least 1 for backoff calc
          val baseWait = min(
            MAX_BACKOFF_MS.toDouble(),
            BASE_BACKOFF_MS * 2.0.pow(backoffAttempts.toDouble())
          ).toLong()
          val jitter = (Math.random() * 0.1 * baseWait).toLong()
          Thread.sleep(baseWait + jitter)
        }

        // Process all items in BATCH_SIZE_LIMIT chunks, collect all unprocessed
        val stillUnprocessed = mutableListOf<I>()
        
        unprocessedItems.chunked(BATCH_SIZE_LIMIT).forEach { chunk ->
          val writeRequests = createWriteRequests(chunk)
          val batchRequest = BatchWriteItemRequest.builder()
            .requestItems(mapOf(dynamoDbTable.tableName() to writeRequests))
            .build()

          val response = dynamoDbClient.batchWriteItem(batchRequest)
          stillUnprocessed.addAll(getUnprocessedItems(response))
        }

        totalAttempts++
        
        // Check if we made any progress
        if (stillUnprocessed.size == unprocessedItems.size) {
          // No progress made, increment stuck counter
          stuckRetryCount++
          if (stuckRetryCount >= MAX_RETRY_ATTEMPTS) {
            throw DynamoDbBatchWriteException(
              """Failed to make progress after $MAX_RETRY_ATTEMPTS attempts.
                 |Total attempts: $totalAttempts
                 |Initial batch size: ${itemsToSave.size}
                 |Remaining unprocessed items: ${stillUnprocessed.size}""".trimMargin()
            )
          }
        } else {
          // Made some progress, reset stuck counter
          stuckRetryCount = 0
        }

        unprocessedItems = stillUnprocessed
      }
    }
  }

  private fun createWriteRequests(items: List<I>): List<WriteRequest> {
    return items.map { item ->
      WriteRequest.builder()
        .putRequest(
          PutRequest.builder()
            .item(dynamoDbTable.tableSchema().itemToMap(item, true))
            .build()
        )
        .build()
    }
  }

  private fun getUnprocessedItems(response: BatchWriteItemResponse): List<I> {
    if (!response.hasUnprocessedItems() || response.unprocessedItems().isEmpty()) {
      return emptyList()
    }

    return response.unprocessedItems()[dynamoDbTable.tableName()]
      ?.mapNotNull { writeRequest ->
        // Convert WriteRequest back to original item type using the table schema
        writeRequest.putRequest()?.item()?.let { 
          dynamoDbTable.tableSchema().mapToItem(it)
        }
      }
      ?: emptyList()
  }

  /**
   * Called for each matching record. Returns true to save the item after returning; false to not
   * save the item.
   */
  abstract fun runOne(item: I, config: BackfillConfig<P>): Boolean
}

class DynamoDbBatchWriteException(message: String) : RuntimeException(message)
