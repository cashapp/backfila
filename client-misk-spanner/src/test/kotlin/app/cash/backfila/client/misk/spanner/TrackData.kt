package app.cash.backfila.client.misk.spanner

class TrackData {
  companion object {
    val TABLE_NAME = "track_data"
    val COLUMNS = Column.values()
  }

  enum class Column {
    id,
    album_token,
    track_title,
    album_title,
    artist_name,
  }
}
