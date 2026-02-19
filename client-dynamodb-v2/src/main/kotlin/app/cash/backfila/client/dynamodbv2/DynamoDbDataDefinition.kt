package app.cash.backfila.client.dynamodbv2

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ScanRequest

/**
 * Describes the DynamoDB data set and structure - what data to process.
 * This includes table information, filtering, and indexing.
 */
open class DynamoDbDataDefinition<I : Any>(
  /**
   * DynamoDB table object used to issue queries, convert to/from rich types and get metadata.
   */
  val dynamoDbTable: DynamoDbTable<I>,

  /** See [ScanRequest.setIndexName]. The default, `null`, means this is not set. */
  val indexName: String? = null,

  /** See [ScanRequest.setFilterExpression]. The default, `null`, means this is not set. */
  val filterExpression: String? = null,

  /** See [ScanRequest.setExpressionAttributeValues]. The default, `null`, means this is not set. */
  val expressionAttributeValues: Map<String, AttributeValue>? = null,

  /** See [ScanRequest.setExpressionAttributeNames]. The default, `null`, means this is not set. */
  val expressionAttributeNames: Map<String, String>? = null,
) {

  companion object {
    @JvmStatic
    fun <I : Any> builder(): Builder<I> {
      return Builder()
    }
  }

  class Builder<I : Any> {
    private var dynamoDbTable: DynamoDbTable<I>? = null
    private var indexName: String? = null
    private var filterExpression: String? = null
    private var expressionAttributeValues: Map<String, AttributeValue>? = null
    private var expressionAttributeNames: Map<String, String>? = null

    /** See [DynamoDbDataDefinition.dynamoDbTable]. */
    fun dynamoDbTable(dynamoDbTable: DynamoDbTable<I>): Builder<I> {
      this.dynamoDbTable = dynamoDbTable
      return this
    }

    /** See [DynamoDbDataDefinition.indexName]. */
    fun indexName(indexName: String): Builder<I> {
      this.indexName = indexName
      return this
    }

    /** See [DynamoDbDataDefinition.filterExpression]. */
    fun filterExpression(filterExpression: String): Builder<I> {
      this.filterExpression = filterExpression
      return this
    }

    /** See [DynamoDbDataDefinition.expressionAttributeValues]. */
    fun expressionAttributeValues(values: Map<String, AttributeValue>): Builder<I> {
      this.expressionAttributeValues = values
      return this
    }

    /** See [DynamoDbDataDefinition.expressionAttributeNames]. */
    fun expressionAttributeNames(names: Map<String, String>): Builder<I> {
      this.expressionAttributeNames = names
      return this
    }

    fun build(): DynamoDbDataDefinition<I> {
      return DynamoDbDataDefinition(
        dynamoDbTable = requireNotNull(dynamoDbTable) { "DynamoDbTable is a required property" },
        indexName = indexName,
        filterExpression = filterExpression,
        expressionAttributeValues = expressionAttributeValues,
        expressionAttributeNames = expressionAttributeNames,
      )
    }
  }
}
