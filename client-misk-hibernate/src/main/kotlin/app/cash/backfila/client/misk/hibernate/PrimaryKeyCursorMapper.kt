package app.cash.backfila.client.misk.hibernate

import javax.inject.Inject
import okio.ByteString

internal class PrimaryKeyCursorMapper @Inject constructor(
  private val primaryKeyAdapters: Map<Class<*>, PrimaryKeyCursorAdapter<*>>,
) {
  fun <PK : Any> fromByteString(type: Class<PK>, cursor: ByteString): Result<PK> {
    val adapter = adapter(type)
    return adapter.fromByteString(cursor)
  }

  fun <PK : Any> toByteString(pkey: PK): ByteString {
    val adapter = adapter(pkey::class.java)
    return adapter.toByteString(pkey)
  }

  private fun <PK : Any> adapter(type: Class<out PK>): PrimaryKeyCursorAdapter<PK> {
    val adapter = primaryKeyAdapters[type] ?: throw IllegalArgumentException(
      "Unregistered backfill primary key type: $type." +
        " Bind a [PrimaryKeyCursorAdapterModule<PKEY>] or use a different type as your primary key."
    )

    @Suppress("UNCHECKED_CAST") // We use runtime checks to guarantee casts are safe.
    return adapter as PrimaryKeyCursorAdapter<PK>
  }
}
