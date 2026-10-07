package app.cash.backfila.client.dynamodbv2

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.dynamodbv2.internal.AwsAttributeValueAdapter
import app.cash.backfila.client.dynamodbv2.internal.DynamoDbBackfillOperator
import app.cash.backfila.client.dynamodbv2.internal.DynamoDbKeyRangeCodec
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.RunBatchRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import software.amazon.awssdk.services.dynamodb.model.ScanResponse

@Suppress("DEPRECATION")
class DynamoDbBatchSizeTest {
  private val client = RecordingDynamoDbClient()
  private val backfill = RecordingBackfill(
    DynamoDbEnhancedClient.builder().dynamoDbClient(client).build()
      .table(TrackItem.TABLE_NAME, TableSchema.fromClass(TrackItem::class.java)),
  )
  private val codec = DynamoDbKeyRangeCodec(
    Moshi.Builder().add(AwsAttributeValueAdapter).add(KotlinJsonAdapterFactory()).build(),
  )
  private val operator = DynamoDbBackfillOperator(
    client, backfill, BackfilaParametersOperator(NoParameters::class), codec,
  )
  private val range = codec.encodeKeyRange(2, 3, 4)
  private val cursor = item("b")

  @Test
  fun `each call processes one page and resumes with the current batch size`() {
    client.responses.add(page(listOf(item("a"), item("b")), 2, cursor))
    client.responses.add(page(listOf(item("c")), 1))

    val first = operator.runBatch(request(range, 2))

    assertThat(client.requests).hasSize(1)
    assertThat(client.requests.single().limit()).isEqualTo(2)
    assertThat(client.requests.single().exclusiveStartKey()).isEmpty()
    assertThat(backfill.batches).containsExactly(listOf("a", "b"))
    assertThat(first.scanned_record_count).isEqualTo(2L)
    assertThat(first.matching_record_count).isEqualTo(2L)
    assertThat(codec.decodeKeyRange(first.remaining_batch_range!!).lastEvaluatedKey).isEqualTo(cursor)

    val second = operator.runBatch(request(first.remaining_batch_range!!, 1))

    assertThat(client.requests).hasSize(2)
    assertThat(client.requests.last().exclusiveStartKey()).isEqualTo(cursor)
    assertThat(client.requests.last().segment()).isEqualTo(2)
    assertThat(client.requests.last().totalSegments()).isEqualTo(4)
    assertThat(client.requests.last().limit()).isEqualTo(1)
    assertThat(backfill.batches).containsExactly(listOf("a", "b"), listOf("c"))
    assertThat(second.scanned_record_count).isEqualTo(1L)
    assertThat(second.matching_record_count).isEqualTo(1L)
    assertThat(second.remaining_batch_range).isNull()
  }

  @Test
  fun `an empty filtered page yields its cursor without scanning again`() {
    client.responses.add(page(emptyList(), 2, cursor))
    client.responses.add(page(listOf(item("c")), 1))

    val first = operator.runBatch(request(range, 2))

    assertThat(client.requests).hasSize(1)
    assertThat(backfill.batches).containsExactly(emptyList())
    assertThat(first.scanned_record_count).isEqualTo(2L)
    assertThat(first.matching_record_count).isZero()
    assertThat(first.remaining_batch_range).isNotNull()

    val second = operator.runBatch(request(first.remaining_batch_range!!, 2))

    assertThat(client.requests).hasSize(2)
    assertThat(client.requests.last().exclusiveStartKey()).isEqualTo(cursor)
    assertThat(backfill.batches).containsExactly(emptyList(), listOf("c"))
    assertThat(second.scanned_record_count).isEqualTo(1L)
    assertThat(second.matching_record_count).isEqualTo(1L)
    assertThat(second.remaining_batch_range).isNull()
  }

  @Test
  fun `a short page yields and an empty terminal cursor completes the segment`() {
    // DynamoDB can stop before the item limit when a page reaches its byte limit.
    client.responses.add(page(listOf(item("a")), 1, cursor))
    client.responses.add(page(emptyList(), 0, emptyMap()))

    val first = operator.runBatch(request(range, 2))

    assertThat(client.requests).hasSize(1)
    assertThat(first.scanned_record_count).isEqualTo(1L)
    assertThat(first.matching_record_count).isEqualTo(1L)
    assertThat(first.remaining_batch_range).isNotNull()

    val second = operator.runBatch(request(first.remaining_batch_range!!, 2))

    assertThat(client.requests).hasSize(2)
    assertThat(second.scanned_record_count).isZero()
    assertThat(second.matching_record_count).isZero()
    assertThat(second.remaining_batch_range).isNull()
  }

  private fun request(range: KeyRange, batchSize: Long) = RunBatchRequest.Builder()
    .backfill_id("test")
    .partition_name("0 of 1")
    .batch_range(range)
    .batch_size(batchSize)
    .dry_run(false)
    .build()

  private fun item(value: String) = mapOf(
    "album_token" to AttributeValue.builder().s(value).build(),
    "sort_key" to AttributeValue.builder().s(value).build(),
  )

  private fun page(
    items: List<Map<String, AttributeValue>>,
    scannedCount: Int,
    lastEvaluatedKey: Map<String, AttributeValue>? = null,
  ): ScanResponse = ScanResponse.builder()
    .items(items)
    .count(items.size)
    .scannedCount(scannedCount)
    .lastEvaluatedKey(lastEvaluatedKey)
    .build()

  private class RecordingBackfill(
    private val table: DynamoDbTable<TrackItem>,
  ) : DynamoDbBackfill<TrackItem, NoParameters>() {
    val batches = mutableListOf<List<String?>>()

    override fun dynamoDbTable() = table

    override fun runBatch(items: List<TrackItem>, config: BackfillConfig<NoParameters>) {
      batches += items.map { it.album_token }
    }
  }

  private class RecordingDynamoDbClient : DynamoDbClient {
    val requests = mutableListOf<ScanRequest>()
    val responses = ArrayDeque<ScanResponse>()

    override fun scan(request: ScanRequest): ScanResponse {
      requests += request
      return responses.removeFirst()
    }

    override fun serviceName() = "dynamodb"

    override fun close() {}
  }
}
