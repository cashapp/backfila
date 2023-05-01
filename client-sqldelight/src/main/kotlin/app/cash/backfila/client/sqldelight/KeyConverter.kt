package app.cash.backfila.client.sqldelight

import okio.ByteString

interface KeyConverter<T> {

  fun toKeyOrNull(bytes: ByteString?): T?

  fun toKey(bytes: ByteString): T

  fun toBytes(value: T): ByteString
}
