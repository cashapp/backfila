package app.cash.backfila.client.fixedset

/**
 * Sample item in the [FixedSetDatastore].
 *
 * Not safe for concurrent use.
 */
data class FixedSetRow(
  val id: Long,
  var value: String
)
