package app.cash.backfila.client

enum class BackfillUnit(val displayName: String) {
  RECORDS("Records"),
  BYTES("Bytes"),
  SEGMENTS("Segments"),
}
