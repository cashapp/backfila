package app.cash.backfila.client.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

@DynamoDBTable(tableName = "music_tracks")
class TrackItem {
  @DynamoDBHashKey
  var album_token: String? = null

  // Either an album info or a track number
  @DynamoDBRangeKey
  var sort_key: String? = null

  @DynamoDBIndexHashKey(globalSecondaryIndexName = "trackTitleIndex", attributeName = "track_title")
  @DynamoDBAttribute
  var track_title: String? = null

  @DynamoDBIndexRangeKey(globalSecondaryIndexName = "trackTitleIndex", attributeName = "album_title")
  @DynamoDBAttribute
  var album_title: String? = null

  @DynamoDBAttribute
  var artist_name: String? = null
}
