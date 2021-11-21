package app.cash.backfila.client.misk.hibernate

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

internal object StringPrimaryKeyCursorAdapter : PrimaryKeyCursorAdapter<String> {
  override fun toByteString(pk: String): ByteString {
    return pk.encodeUtf8()
  }

  override fun fromByteString(byteString: ByteString): Result<String> {
    return Result.success(byteString.utf8())
  }
}
