package app.cash.backfila.client.misk.hibernate

import misk.hibernate.DbEntity
import misk.hibernate.Id
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

internal object IdPrimaryKeyCursorAdapter : PrimaryKeyCursorAdapter<Id<*>> {
  override fun toByteString(pk: Id<*>): ByteString {
    return pk.toString().encodeUtf8()
  }

  override fun fromByteString(byteString: ByteString): Result<Id<*>> {
    val id = byteString.utf8().toLongOrNull()
      ?: return Result.failure(NumberFormatException())
    return Result.success(Id<DbEntity<*>>(id))
  }
}
