package app.cash.backfila.client.sqldelight

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

object IntKeyEncoder : KeyEncoder<Int> {

  override fun decode(bytes: ByteString): Int = bytes.utf8().toIntOrNull()
    ?: throw NullPointerException("Integer key is invalid or null: $bytes")

  override fun encode(value: Int): ByteString = value.toString().encodeUtf8()
}

object LongKeyEncoder : KeyEncoder<Long> {

  override fun decode(bytes: ByteString): Long = bytes.utf8().toLongOrNull()
    ?: throw NullPointerException("Long integer key is invalid or null: $bytes")

  override fun encode(value: Long): ByteString = value.toString().encodeUtf8()
}

object StringKeyEncoder : KeyEncoder<String> {

  override fun decode(bytes: ByteString): String = bytes.utf8()

  override fun encode(value: String): ByteString = value.encodeUtf8()
}
