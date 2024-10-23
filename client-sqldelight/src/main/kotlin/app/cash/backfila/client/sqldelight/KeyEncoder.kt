package app.cash.backfila.client.sqldelight

import okio.ByteString

interface KeyEncoder<T> {

  fun encode(value: T): ByteString

  fun decode(bytes: ByteString): T
}
