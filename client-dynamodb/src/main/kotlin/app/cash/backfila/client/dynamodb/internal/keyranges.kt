package app.cash.backfila.client.dynamodb.internal

import app.cash.backfila.client.dynamodb.ForDynamoDbBackend
import app.cash.backfila.protos.clientservice.KeyRange
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.google.inject.util.Types
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import javax.inject.Inject
import javax.inject.Singleton
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * With DynamoDB each run selects the number of segments so we need begin, end, and count to
 * uniquely identify a range.
 */
internal data class DynamoDbKeyRange(
  val start: Int,
  val end: Int,
  val count: Int,
  val lastEvaluatedKey: Map<String, AttributeValue>? = null
) {
  init {
    // Only allow lastEvaluatedKey to be non-null if the range includes a single segment.
    if (lastEvaluatedKey != null) {
      require(end == start + 1)
    }
  }
}

/**
 * It encodes the key range in utf8 for better cursor visibility in backfila.
 * It's form:
 * start = "v2:<segment_offset>/<segment_count>:<last_evaluated_key_json>"
 * end = "v2:<segment_offset>/<segment_count>"
 */
@Singleton
class DynamoDbKeyRangeCodec @Inject constructor(
  @ForDynamoDbBackend moshi: Moshi
) {
  private val adapter = moshi.adapter<Map<String, AttributeValue>>(
    Types.newParameterizedType(Map::class.java, String::class.java, AttributeValue::class.java)
  )

  companion object {
    const val VERSION = "v2"
    const val INT_FORMAT = "%010d"
  }

  internal fun decodeKeyRange(keyRange: KeyRange): DynamoDbKeyRange {
    val startSegment = decodeSegment(keyRange.start)
    val endSegment = decodeSegment(keyRange.end)

    // start and end must have the same total segment count.
    require(startSegment.count == endSegment.count)

    // end cannot have a lastEvaluatedKey
    require(endSegment.lastEvaluatedKey == null)

    return DynamoDbKeyRange(
      startSegment.offset,
      endSegment.offset,
      startSegment.count,
      startSegment.lastEvaluatedKey
    )
  }

  internal fun decodeSegment(segment: ByteString): SegmentData {
    val buffer = Buffer().write(segment)
    val tag = buffer.readUtf8(2)
    require(tag == VERSION) {
      "Encountered an incorrect version $tag instead of $VERSION . Make sure any deploys have " +
        "completed and re-create the backfill."
    }
    require(buffer.readUtf8(1) == ":")
    val offset = buffer.readUtf8(10).toInt()
    require(buffer.readUtf8(1) == "/")
    val count = buffer.readUtf8(10).toInt()
    var lastEvaluatedKey: Map<String, AttributeValue>? = null
    if (!buffer.exhausted()) { // has a lastEvaluatedKey
      require(buffer.readUtf8(1) == ":")
      lastEvaluatedKey = adapter.fromJson(buffer)
    }
    require(buffer.exhausted())
    return SegmentData(offset, count, lastEvaluatedKey)
  }

  internal fun encodeKeyRange(
    start: Int,
    end: Int,
    count: Int,
    lastEvaluatedKey: Map<String, AttributeValue>? = null
  ): KeyRange {
    return KeyRange.Builder()
      .start(encodeSegment(start, count, lastEvaluatedKey))
      .end(encodeSegment(end, count))
      .build()
  }

  private fun encodeSegment(
    offset: Int,
    count: Int,
    lastEvaluatedKey: Map<String, AttributeValue>? = null
  ): ByteString {
    require(offset in 0..count)
    val stringOffset = INT_FORMAT.format(offset)
    val stringCount = INT_FORMAT.format(count)
    val buffer = Buffer()
      .writeUtf8("$VERSION:$stringOffset/$stringCount")
    if (lastEvaluatedKey != null) {
      buffer.writeUtf8(":").writeUtf8(adapter.toJson(lastEvaluatedKey))
    }
    return buffer.readByteString()
  }

  internal data class SegmentData(
    val offset: Int,
    val count: Int,
    val lastEvaluatedKey: Map<String, AttributeValue>?
  )

  internal data class AttributeValueJson(
    var s: String? = null,
    val n: String? = null,
    val b: ByteString? = null,
    val sS: List<String>? = null,
    val nS: List<String>? = null,
    val bS: List<ByteString>? = null,
    val m: Map<String, AttributeValueJson>? = null,
    val l: List<AttributeValueJson>? = null,
    val nULLValue: Boolean? = null,
    val bOOL: Boolean? = null
  )
}

object AwsAttributeValueAdapter {
  @ToJson internal fun toJson(attributeValue: AttributeValue): DynamoDbKeyRangeCodec.AttributeValueJson {
    return DynamoDbKeyRangeCodec.AttributeValueJson(
      attributeValue.s,
      attributeValue.n,
      attributeValue.b?.toByteString(),
      attributeValue.ss,
      attributeValue.ns,
      attributeValue.bs?.map { it.toByteString() },
      attributeValue.m?.mapValues { toJson(it.value) },
      attributeValue.l?.map { toJson(it) },
      attributeValue.getNULL(),
      attributeValue.bool
    )
  }

  @FromJson internal fun fromJson(json: DynamoDbKeyRangeCodec.AttributeValueJson): AttributeValue {
    return AttributeValue()
      .withS(json.s)
      .withN(json.n)
      .withB(json.b?.asByteBuffer())
      .withSS(json.sS)
      .withNS(json.nS)
      .withBS(json.bS?.map { it.asByteBuffer() })
      .withM(json.m?.mapValues { fromJson(it.value) })
      .withL(json.l?.map { fromJson(it) })
      .withNULL(json.nULLValue)
      .withBOOL(json.bOOL)
  }
}
