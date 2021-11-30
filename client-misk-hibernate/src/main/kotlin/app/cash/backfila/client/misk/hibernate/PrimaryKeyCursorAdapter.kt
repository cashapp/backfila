package app.cash.backfila.client.misk.hibernate

import okio.ByteString

/**
 * Converts primary key values to [ByteString] representations to persist as cursors in backfills.
 *
 * Any primary key type that is used with Backfila must be convertible to and from [ByteString]s
 * to facilitate cursor persistence and display in the UI.
 */
interface PrimaryKeyCursorAdapter<PK : Any> {
  fun toByteString(pk: PK): ByteString
  fun fromByteString(byteString: ByteString): Result<PK>
}
