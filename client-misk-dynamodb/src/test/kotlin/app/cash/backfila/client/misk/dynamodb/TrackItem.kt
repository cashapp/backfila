package app.cash.backfila.client.misk.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable

@DynamoDBTable(tableName = "music_tracks")
class TrackItem {
  @DynamoDBHashKey
  var album_token: String? = null

  @DynamoDBRangeKey
  var track_token: String? = null

  @DynamoDBAttribute
  var track_title: String? = null
}
