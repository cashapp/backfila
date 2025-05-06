package app.cash.backfila.client.dynamodbv2

import app.cash.backfila.client.BackfillConfig
import software.amazon.awssdk.core.exception.ApiCallTimeoutException
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
 * Implements retry logic with exponential backoff for:
 * - Unprocessed items from BatchWriteItem operations
 * - API timeouts
 * 
 * The retry counter for unprocessed items only starts when we stop making progress 
 * (no items processed in a round). The timeout counter only increments when all chunks
 * in an iteration timeout.
 * 
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
      var consecutiveTimeouts = 0
      var totalAttempts = 0

      while (unprocessedItems.isNotEmpty()) {
        if (totalAttempts > 0) {
          // Calculate backoff time with exponential increase and jitter
          // Use the larger of stuckRetryCount or consecutiveTimeouts to determine backoff
          val backoffAttempts = maxOf(stuckRetryCount, consecutiveTimeouts).coerceAtLeast(1)
          val baseWait = min(
            MAX_BACKOFF_MS.toDouble(),
            BASE_BACKOFF_MS * 2.0.pow(backoffAttempts.toDouble())
          ).toLong()
          val jitter = (Math.random() * 0.1 * baseWait).toLong()
          Thread.sleep(baseWait + jitter)
        }

        // Process all items in BATCH_SIZE_LIMIT chunks, collect all unprocessed
        val stillUnprocessed = mutableListOf<I>()
        var batchSucceeded = false
        var lastTimeoutException: ApiCallTimeoutException? = null
        var hadTimeoutThisIteration = false
        var allChunksTimedOut = true
        
        unprocessedItems.chunked(BATCH_SIZE_LIMIT).forEach { chunk ->
          try {
            val writeRequests = createWriteRequests(chunk)
            val batchRequest = BatchWriteItemRequest.builder()
              .requestItems(mapOf(dynamoDbTable.tableName() to writeRequests))
              .build()

            val response = dynamoDbClient.batchWriteItem(batchRequest)
            stillUnprocessed.addAll(getUnprocessedItems(response))
            batchSucceeded = true
            allChunksTimedOut = false
          } catch (e: ApiCallTimeoutException) {
            // On timeout, only add the current chunk to unprocessed items
            stillUnprocessed.addAll(chunk)
            hadTimeoutThisIteration = true
            lastTimeoutException = e
          }
        }

        // Handle timeout tracking
        if (hadTimeoutThisIteration && allChunksTimedOut) {
          consecutiveTimeouts++
          if (consecutiveTimeouts >= MAX_RETRY_ATTEMPTS) {
            throw DynamoDbBatchWriteException(
              """Failed due to consecutive complete timeouts after $MAX_RETRY_ATTEMPTS attempts.
                 |Total attempts: $totalAttempts
                 |Initial batch size: ${itemsToSave.size}
                 |Remaining unprocessed items: ${unprocessedItems.size}""".trimMargin(),
              lastTimeoutException
            )
          }
        } else if (batchSucceeded) {
          // Reset timeout counter if any chunk succeeded
          consecutiveTimeouts = 0
        }
        
        // If we saw any timeouts but didn't hit the consecutive limit, include the last exception
        // as suppressed to maintain error context
        if (lastTimeoutException != null && !allChunksTimedOut) {
          lastTimeoutException!!.addSuppressed(
            IllegalStateException(
              """Saw partial timeouts while processing batches.
                 |Successfully processed some chunks, continuing with retries.""".trimMargin()
            )
          )
        }

        totalAttempts++
        
        // Check if we made any progress with unprocessed items
        if (batchSucceeded && stillUnprocessed.size == unprocessedItems.size) {
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
        } else if (batchSucceeded) {
          // Made some progress or had different number of unprocessed items
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

class DynamoDbBatchWriteException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
