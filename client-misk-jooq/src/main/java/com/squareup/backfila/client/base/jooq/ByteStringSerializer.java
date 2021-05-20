package com.squareup.backfila.client.base.jooq;

import java.util.function.Function;
import okio.ByteString;

/**
 * Responsible for serializing and deserializing values of a particular type into and from
 * {@link ByteString}s.
 *
 * @param <T> the type of value
 */
public interface ByteStringSerializer<T> {
  /**
   * Serializes the given value to a {@link ByteString}.
   */
  ByteString toByteString(T value);

  /**
   * Deserializes the given {@link ByteString} to a value.
   */
  T fromByteString(ByteString byteString);

  /**
   * Provides a way to compose a serializer with additional transformation logic. This can be useful,
   * for example, if you already have functions that convert to/from an intermediary type for which
   * an existing serializer exists. For example `forString` can be composed with functions that
   * encode/decode a value object as a JSON string.
   *
   * @param serialize functions that converts from your type to the T type.
   * @param deserialize function that converts from the T type to your type.
   * @param <U> your type
   */
  default <U> ByteStringSerializer<U> composeWith(Function<U, T> serialize, Function<T, U> deserialize) {
    ByteStringSerializer<T> outerSerializer = this;
    return new ByteStringSerializer<U>() {
      @Override public ByteString toByteString(U value) {
        return outerSerializer.toByteString(serialize.apply(value));
      }

      @Override public U fromByteString(ByteString byteString) {
        return deserialize.apply(outerSerializer.fromByteString(byteString));
      }
    };
  }

  /**
   * A {@link ByteStringSerializer} implementation for {@link Long} values.
   *
   * The values are serialized as UTF-8 encoded strings of the decimal representation of the long
   * value.
   */
  ByteStringSerializer<Long> forLong = build(
        id -> ByteString.encodeUtf8(Long.toString(id)),
        byteString -> Long.parseLong(byteString.utf8()));

  /**
   * A {@link ByteStringSerializer} implementation for {@link String} values.
   *
   * The values are serialized as UTF-8 encoded strings.
   */
  ByteStringSerializer<String> forString = build(
          value -> ByteString.encodeUtf8(value),
          byteString -> byteString.utf8());

  /**
   * A {@link ByteStringSerializer} implementation for `byte[]` values.
   *
   * The values are serialized by wrapping the byte array in a ByteString.
   */
  ByteStringSerializer<byte[]> forByteArray = build(
      ByteString::of,
      ByteString::toByteArray);

  /**
   * Helper method that makes it easy to build a serializer, based on two simple lambdas.
   */
  static <T> ByteStringSerializer<T> build(
      Function<T, ByteString> toByteString,
      Function<ByteString, T> fromByteString) {
    return new ByteStringSerializer<T>() {
      @Override public ByteString toByteString(T value) {
        return toByteString.apply(value);
      }

      @Override public T fromByteString(ByteString byteString) {
        return fromByteString.apply(byteString);
      }
    };
  }
}
