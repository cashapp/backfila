package app.cash.backfila.client.misk.dynamodb.internal

import app.cash.backfila.protos.clientservice.KeyRange
import okio.Buffer
import okio.ByteString

/**
 * With DynamoDB each run selects the number of segments so we need begin, end, and count to
 * uniquely identify a range.
 */
internal data class DynamoDbKeyRange(
  val start: Int,
  val end: Int,
  val count: Int
)

internal fun decodeKeyRange(keyRange: KeyRange): DynamoDbKeyRange {
  val (startOffset, count) = decodeSegment(keyRange.start)
  val (endOffset, _) = decodeSegment(keyRange.end)
  return DynamoDbKeyRange(startOffset, endOffset, count)
}

internal fun decodeSegment(segment: ByteString): Pair<Int, Int> {
  val buffer = Buffer().write(segment)
  val tag = buffer.readUtf8(2)
  require(tag == "v1")
  val offset = buffer.readInt()
  val count = buffer.readInt()
  return offset to count
}

internal fun encodeKeyRange(
  start: Int,
  end: Int,
  count: Int
): KeyRange {
  return KeyRange.Builder()
      .start(encodeSegment(start, count))
      .end(encodeSegment(end, count))
      .build()
}

internal fun encodeSegment(offset: Int, count: Int): ByteString {
  require(offset in 0..count)
  return Buffer()
      .writeUtf8("v1")
      .writeInt(offset)
      .writeInt(count)
      .readByteString()
}
