package app.cash.backfila.client.dynamodbv2

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey

@DynamoDbBean()
class TrackItem {
  companion object {
    const val TABLE_NAME = "music_tracks"
  }

  @get:DynamoDbPartitionKey
  var album_token: String? = null

  // Either an album info or a track number
  @get:DynamoDbSortKey
  var sort_key: String? = null

  var track_title: String? = null

  var album_title: String? = null

  var artist_name: String? = null
}
