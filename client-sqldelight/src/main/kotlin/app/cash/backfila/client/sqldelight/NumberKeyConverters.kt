package app.cash.backfila.client.sqldelight

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

class IntKeyConverter : KeyConverter<Int> {

  override fun toKeyOrNull(bytes: ByteString?): Int? = bytes?.utf8()?.toIntOrNull()

  override fun toKey(bytes: ByteString): Int = toKeyOrNull(bytes)
    ?: throw NullPointerException("Integer key is invalid or null")

  override fun toBytes(value: Int): ByteString = value.toString().encodeUtf8()
}

class LongKeyConverter : KeyConverter<Long> {

  override fun toKeyOrNull(bytes: ByteString?): Long? = bytes?.utf8()?.toLongOrNull()

  override fun toKey(bytes: ByteString): Long = toKeyOrNull(bytes)
    ?: throw NullPointerException("Long integer key is invalid or null")

  override fun toBytes(value: Long): ByteString = value.toString().encodeUtf8()
}

class StringKeyConverter : KeyConverter<String> {

  override fun toKeyOrNull(bytes: ByteString?): String? = bytes?.utf8()

  override fun toKey(bytes: ByteString): String = toKeyOrNull(bytes)
    ?: throw NullPointerException("Long integer key is invalid or null")

  override fun toBytes(value: String): ByteString = value.encodeUtf8()
}
