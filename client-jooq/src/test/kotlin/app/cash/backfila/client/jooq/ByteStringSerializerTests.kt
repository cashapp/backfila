package app.cash.backfila.client.jooq

import okio.ByteString
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class ByteStringSerializerTest {
  @Test fun forLongCanRoundTripLongValues() {
    val value: Long = Long.MAX_VALUE
    val byteStringSerializer = ByteStringSerializer.forLong
    val serialized: ByteString = byteStringSerializer.toByteString(value)
    val actual = byteStringSerializer.fromByteString(serialized)
    assertThat(actual).isEqualTo(value)
  }

  @Test
  fun forLongProducesHumanReadableString() {
    val value = 223L
    val byteStringSerializer = ByteStringSerializer.forLong
    assertThat(byteStringSerializer.toByteString(value).toString()).contains("223")
  }

  @Test
  fun forByteArrayCanRoundTripByteArrayValues() {
    val value = byteArrayOf(8, 7, 6)
    val byteStringSerializer = ByteStringSerializer.forByteArray
    val serialized: ByteString = byteStringSerializer.toByteString(value)
    val actual = byteStringSerializer.fromByteString(serialized)
    assertThat(actual).isEqualTo(value)
  }
}
