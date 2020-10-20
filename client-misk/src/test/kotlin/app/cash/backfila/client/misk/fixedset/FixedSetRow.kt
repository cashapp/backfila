package app.cash.backfila.client.misk.fixedset

/**
 * Sample item in the [FixedSetDatastore].
 *
 * Not safe for concurrent use.
 */
data class FixedSetRow(
  val id: Long,
  var value: String
)
