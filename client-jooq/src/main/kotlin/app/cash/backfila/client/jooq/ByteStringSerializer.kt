package app.cash.backfila.client.jooq

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

interface ByteStringSerializer<T> {
  /**
   * Serializes the given value to a [ByteString].
   */
  fun toByteString(value: T): ByteString

  /**
   * Deserializes the given [ByteString] to a value.
   */
  fun fromByteString(byteString: ByteString): T

  /**
   * Provides a way to compose a serializer with additional transformation logic. This can be useful,
   * for example, if you already have functions that convert to/from an intermediary type for which
   * an existing serializer exists. For example `forString` can be composed with functions that
   * encode/decode a value object as a JSON string.
   *
   * @param serialize functions that converts from your type to the T type.
   * @param deserialize function that converts from the T type to your type.
   * @param <U> your type
   </U> */
  fun <U> composeWith(
    serialize: (serialiseTo: U) -> T,
    deserialize: (deserialiseFrom: T) -> U
  ): ByteStringSerializer<U> {
    val outerSerializer = this
    return object : ByteStringSerializer<U> {
      override fun toByteString(value: U): ByteString {
        return outerSerializer.toByteString(serialize(value))
      }

      override fun fromByteString(byteString: ByteString): U {
        return deserialize(outerSerializer.fromByteString(byteString))
      }
    }
  }

  companion object {
    /**
     * Helper method that makes it easy to build a serializer, based on two simple lambdas.
     */
    fun <T> build(
      toByteString: (value: T) -> ByteString,
      fromByteString: (byteString: ByteString) -> T
    ): ByteStringSerializer<T> {
      return object : ByteStringSerializer<T> {
        override fun toByteString(value: T): ByteString {
          return toByteString(value)
        }

        override fun fromByteString(byteString: ByteString): T {
          return fromByteString(byteString)
        }
      }
    }

    /**
     * A [ByteStringSerializer] implementation for [Long] values.
     *
     * The values are serialized as UTF-8 encoded strings of the decimal representation of the long
     * value.
     */
    val forLong = build(
      { id: Long -> id.toString().encodeUtf8() },
      { byteString: ByteString -> byteString.utf8().toLong() }
    )

    /**
     * A [ByteStringSerializer] implementation for [String] values.
     *
     * The values are serialized as UTF-8 encoded strings.
     */
    val forString = build(
      { value: String -> value.encodeUtf8() },
      { byteString: ByteString -> byteString.utf8() }
    )

    /**
     * A [ByteStringSerializer] implementation for `byte[]` values.
     *
     * The values are serialized by wrapping the byte array in a ByteString.
     */
    val forByteArray = build(
      { value: ByteArray -> value.toByteString() },
      { byteString: ByteString -> byteString.toByteArray() }
    )
  }
}
