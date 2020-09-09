package app.cash.backfila.client.misk

import app.cash.tempest.Attribute
import app.cash.tempest.InlineView
import app.cash.tempest.LogicalDb
import app.cash.tempest.LogicalTable
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.google.inject.Provides
import com.google.inject.Singleton
import javax.inject.Inject
import misk.MiskTestingServiceModule
import misk.aws.dynamodb.testing.DockerDynamoDb
import misk.aws.dynamodb.testing.DockerDynamoDbModule
import misk.aws.dynamodb.testing.DynamoDbTable
import misk.inject.KAbstractModule
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

interface MusicTable : LogicalTable<MusicItem> {
  val albumInfo: InlineView<AlbumInfo.Key, AlbumInfo>
  val albumTracks: InlineView<AlbumTrack.Key, AlbumTrack>
}

/*


A_456   INFO_      album_title="Thriller"  artist_name="michael jackson"
A_456   TRACK_T3   track_name="Black or White"
PL_789  INFO_      madeby="swankJesse"  artist_name="best of jackson"
PL_789  TRACK_P1   track_name="Billy Jean"


 */

data class AlbumInfo(
  @Attribute(name = "partition_key")
  val album_token: String,
  val album_title: String,
  val artist_name: String,
  val genre_name: String
) {
  @Attribute(prefix = "INFO_")
  val sort_key: String = ""

  @Transient
  val key = Key(album_token)

  data class Key(
    val album_token: String
  ) {
    val sort_key: String = ""
  }
}

data class AlbumTrack(
  @Attribute(name = "partition_key")
  val album_token: String,
  @Attribute(name = "sort_key", prefix = "TRACK_")
  val track_token: String,
  val track_title: String
) {
  constructor(
    album_token: String,
    track_number: Long,
    track_title: String
  ) : this(album_token, "%016x".format(track_number), track_title)

  @Transient
  val key = Key(album_token, track_token)

  @Transient
  val track_number = track_token.toLong(radix = 16)

  data class Key(
    val album_token: String,
    val track_token: String = ""
  ) {
    constructor(album_token: String, track_number: Long) : this(album_token, "%016x".format(
        track_number))

    @Transient
    val track_number = if (track_token.isEmpty()) 0 else track_token.toLong(radix = 16)
  }
}

@DynamoDBTable(tableName = "music_items")
class MusicItem {
  // All Items.
  @DynamoDBHashKey
  @DynamoDBIndexRangeKey
  var partition_key: String? = null

  @DynamoDBRangeKey
  var sort_key: String? = null

  // AlbumInfo.
  @DynamoDBAttribute
  var album_title: String? = null

  @DynamoDBIndexHashKey
  @DynamoDBAttribute
  var artist_name: String? = null

  @DynamoDBAttribute
  @DynamoDBIndexHashKey
  var genre_name: String? = null

  // AlbumTrack.
  @DynamoDBAttribute
  @DynamoDBIndexRangeKey
  var track_title: String? = null
}

class MusicDbTestModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(DockerDynamoDbModule(DynamoDbTable(MusicItem::class)))
  }

  @Provides
  @Singleton
  fun provideTestDb(amazonDynamoDB: AmazonDynamoDB): MusicDb {
    val dynamoDbMapper = DynamoDBMapper(amazonDynamoDB)
    return LogicalDb(dynamoDbMapper)
  }
}

@MiskTest(startService = true)
class DynamoDbTest {
  @MiskTestModule
  val module = MusicDbTestModule()

  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var musicDb: MusicDb
  private val musicTable get() = musicDb.music

  @Test
  fun loadAfterSave() {
    val albumInfo = AlbumInfo(
        "ALBUM_1",
        "after hours - EP",
        "53 Thieves",
        "Contemporary R&B"
    )
    musicTable.albumInfo.save(albumInfo)

    // Query the movies created.
    val loadedAlbumInfo = musicTable.albumInfo.load(albumInfo.key)!!

    assertThat(loadedAlbumInfo.album_token).isEqualTo(albumInfo.album_token)
    assertThat(loadedAlbumInfo.artist_name).isEqualTo(albumInfo.artist_name)
    assertThat(loadedAlbumInfo.genre_name).isEqualTo(albumInfo.genre_name)
  }
}

interface MusicDb : LogicalDb {
  val music: MusicTable
}
