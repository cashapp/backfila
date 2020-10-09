package app.cash.backfila.client.misk.tempest

import app.cash.tempest.Attribute
import app.cash.tempest.FilterExpression
import app.cash.tempest.InlineView
import app.cash.tempest.LogicalDb
import app.cash.tempest.LogicalTable
import app.cash.tempest.Offset
import app.cash.tempest.Page
import app.cash.tempest.ScanConfig
import app.cash.tempest.WorkerId
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.google.inject.Provides
import com.google.inject.Singleton
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
import javax.inject.Inject


@MiskTest(startService = true)
class DynamoDbTest {
  @MiskTestModule
  val module = MusicDbTestModule()

  @MiskExternalDependency
  val dockerDynamoDb = DockerDynamoDb

  @Inject lateinit var musicDb: MusicDb
  private val musicTable get() = musicDb.music
  @Inject lateinit var musicTestData: MusicTableTestData

  @Test
  fun loadAfterSave() {
    val albumInfo = AlbumInfo(
        "ALBUM_0",
        "after hours - EP",
        "53 Thieves",
        "Contemporary R&B"
    )
    musicTable.albumInfo.save(albumInfo)

    musicTestData.addAllTheMusic()

    // Query the music created.
    val loadedAlbumInfo = musicTable.albumInfo.load(albumInfo.key)!!

    assertThat(loadedAlbumInfo.album_token).isEqualTo(albumInfo.album_token)
    assertThat(loadedAlbumInfo.artist_name).isEqualTo(albumInfo.artist_name)
    assertThat(loadedAlbumInfo.genre_name).isEqualTo(albumInfo.genre_name)
  }

  @Test
  fun tryScan() {

    val workerId = WorkerId(3, 4)
    val config = ScanConfig.Builder()
        .workerId(workerId)
        .filterExpression(isTrack())
        .pageSize(3)
        .build()
    val observedTracks = mutableListOf<AlbumTrack.Key>()

    musicTestData.insertTracksOnly(0 until 8, 0 until 2)
    musicTestData.insertTracksOnly(16 until 24, 0 until 2)

    // TODO: offsets should have a slightly smaller limit, so we will usually get a single page
    // in the scan phase. "10001 so we see the terminal element"

    val ranges = musicTable.albumTracks.collectRanges(config)

    musicTestData.insertTracksOnly(8 until 16, 0 until 2)
    musicTestData.insertTracksOnly(24 until 32, 0 until 2)

    for (range in ranges) {
      musicTable.albumTracks.scanOffsetRange(
          config = config,
          limit = 9,
          range = range,
          keyGetter = { it.key }
      ) { item ->
        println("track ${item.key}")
      }
    }
//
//    musicTable.albumTracks.scanAllItems(config) { content ->
//      observedTracks.add(content.key)
//      println("track ${content.key}")
//
//      if (observedTracks.size == 5) {
//        println("doubling the library")
//        musicTestData.insertTracksOnly(8 until 16, 0 until 2)
//        musicTestData.insertTracksOnly(24 until 32, 0 until 2)
//      }
//
//      return@scanAllItems true
//    }
  }



  @Test fun isScanOrderStable() {
    musicTestData.insertTracksOnly(0 until 8, 0 until 2)
    musicTestData.insertTracksOnly(16 until 24, 0 until 2)

    val numWorkers = 4
    val originalObservedTracks = getTrackByWorkerScan(numWorkers)

    val secondObservedTracks = getTrackByWorkerScan(numWorkers)
    originalObservedTracks.forEach{ (workerIndex, tracks) -> tracks.forEachIndexed{
      index, track -> println( "worker $workerIndex originalkey $track secondkey ${secondObservedTracks.getValue(workerIndex)[index]}") }
    }
    originalObservedTracks.forEach{ (workerIndex, tracks) ->
      assertThat(tracks).containsExactlyElementsOf(secondObservedTracks.getValue(workerIndex))
    }

    musicTestData.insertTracksOnly(8 until 16, 0 until 2)
    musicTestData.insertTracksOnly(24 until 32, 0 until 2)

    val afterMoreInsertsObservedTracks = getTrackByWorkerScan(numWorkers)

    // Each item only shows up once in the scan after inserts?
    afterMoreInsertsObservedTracks.forEach{ (workerIndex, tracks) ->
      assertThat(tracks).containsOnlyOnceElementsOf(originalObservedTracks.getValue(workerIndex))
    }

    // Are they ordered the same after inserts?
    originalObservedTracks.forEach{ (workerIndex, tracks) ->
      var afterMoreIndex = 0
      tracks.forEachIndexed{ originalIndex, track ->
        var advanceNum = 0;
        while (track != afterMoreInsertsObservedTracks.getValue(workerIndex)[afterMoreIndex + advanceNum]) {
          advanceNum++
        }
        println( "worker $workerIndex found same key $track advanced index ${advanceNum} in the after inserts set")
        afterMoreIndex += advanceNum
      }
      println( "worker $workerIndex has ${afterMoreInsertsObservedTracks.getValue(workerIndex).size - afterMoreIndex - 1} items left")
      /*
      afterMoreInsertsObservedTracks.getValue(workerIndex).subList(afterMoreIndex + 1 ,afterMoreInsertsObservedTracks.getValue(workerIndex).size).forEach {
        println("Item left ${it}")
      }*/
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun getTrackByWorkerScan(numWorkers: Int): Map<Int, List<AlbumTrack.Key>> {
    return buildMap<Int, List<AlbumTrack.Key>> {
      for (workerIndex in 0 until numWorkers) {
        put(workerIndex, buildList {
          val workerId = WorkerId(workerIndex, numWorkers)
          val config = ScanConfig.Builder()
              .workerId(workerId)
              .filterExpression(isTrack())
              .pageSize(3)
              .build()
          musicTable.albumTracks.scanAllItems(config) {
            add(it.key)
          }
        })
      }
    }
  }

  private fun isTrack(): FilterExpression {
    return FilterExpression(
        "begins_with(sort_key, :track_prefix)",
        mapOf(
            ":track_prefix" to AttributeValue().withS("TRACK_")
        )
    )
  }

  private fun <K : Any, T : Any> InlineView<K, T>.scanAllPages(
    config: ScanConfig,
    initialOffset: Offset<K>? = null,
    eachPage: (Page<K, T>) -> Boolean
  ) {
    var initialOffset = initialOffset
    while (true) {
      val page = scan(
          initialOffset = initialOffset,
          config = config
      )
      if (!eachPage(page)) break
      if (!page.hasMorePages) break
      initialOffset = page.offset
    }
  }

  private fun <K : Any, T : Any> InlineView<K, T>.scanAllItems(
    config: ScanConfig,
    initialOffset: Offset<K>? = null,
    eachItem: (T) -> Boolean
  ) {
    return scanAllPages(config, initialOffset) { page ->
      for (content in page.contents) {
        if (!eachItem(content)) return@scanAllPages false
      }
      return@scanAllPages true
    }
  }

  data class BackfillRange<K>(
    val from: Offset<K>?,
    val until: Offset<K>?,
  )

  private fun <K : Any, T : Any> InlineView<K, T>.collectRanges(
    config: ScanConfig,
    initialOffset: Offset<K>? = null
  ): List<BackfillRange<K>> {
    val result = mutableListOf<BackfillRange<K>>()
    var lastOffset: Offset<K>? = null
    scanAllPages(config, initialOffset) { page ->
      if (page.hasMorePages) {
        result += BackfillRange(lastOffset, page.offset!!)
      } else {
        result += BackfillRange(lastOffset, null)
      }
      lastOffset = page.offset
      return@scanAllPages true
    }
    return result
  }

  /**
   * @param range from null to scan from the very beginning; no lower bound.
   * @param range until null to scan to the very end; no upper bound.
   */
  private fun <K : Any, T : Any> InlineView<K, T>.scanOffsetRange(
    config: ScanConfig,
    limit: Int,
    range: BackfillRange<K>,
    keyGetter: (T) -> K,
    eachItem: (T) -> Unit
  ) {
    var count = 0
    scanAllPages(config, range.from) { page ->
      for (item in page.contents) {
        if (keyGetter(item) == range.until?.key) return@scanAllPages false
        eachItem(item)
        count++
        if (count == limit) throw Exception("expected to find ${range.until} within $limit")
      }
      return@scanAllPages true
    }
  }
}

class DynamoMusicTableTestData(
  @Inject val musicDb: MusicDb
) {
  private val musicTable get() = musicDb.music

  fun insertTracksOnly(albumRange: IntRange, trackRange: IntRange) {
    for (i in albumRange) {
      for (t in trackRange) {
        musicTable.albumTracks.save(AlbumTrack(
            album_token = "ALBUM_${10000 + i}",
            track_number = t.toLong(),
            track_title = "This is song $t"
        ))
      }
    }
  }

  fun addAllTheMusic() {
    addMichaelJackson()
    addMichaelBuble()
    addLinkinPark()
  }

  fun addMichaelJackson() {
    addOffTheWall()
    addThriller()
  }

  fun addOffTheWall() {
    addAlbum(
        "ALBUM_1",
        "Off the Wall",
        "Michael Jackson",
        "Disco funk pop R&B",
        listOf(
            "Don't Stop 'Til You Get Enough",
            "Rock with You",
            "Working Day and Night",
            "Get on the Floor",
            "Off the Wall",
            "Girlfriend",
            "She's Out of My Life",
            "I Can't Help It",
            "It's the Falling in Love",
            "Burn This Disco Out"))
  }

  fun addThriller() {
    addAlbum(
        "ALBUM_2",
        "Thriller",
        "Michael Jackson",
        "Pop post-disco rock funk",
        listOf(
            "Wanna Be Startin' Somethin'",
            "Baby Be Mine",
            "The Girl Is Mine",
            "Thriller",
            "Beat It",
            "Billie Jean",
            "Human Nature",
            "P.Y.T. (Pretty Young Thing)",
            "The Lady in My Life"))
  }

  fun addMichaelBuble() {
    addAlbum(
        "ALBUM_3",
        "Michael Bublé",
        "Michael Bublé",
        "Big band smooth jazz jazz traditional pop adult contemporary",
        listOf("Fever",
            "Moondance",
            "Kissing a Fool",
            "For Once in My Life",
            "How Can You Mend a Broken Heart",
            "Summer Wind",
            "You'll Never Find Another Love like Mine",
            "Crazy Little Thing Called Love",
            "Put Your Head on My Shoulder",
            "Sway",
            "The Way You Look Tonight",
            "Come Fly with Me",
            "That's All"))
  }

  fun addLinkinPark() {
    addAlbum(
        "ALBUM_4",
        "Hybrid Theory",
        "Linkin Park",
        "Nu metal rap metal alternative metal rap rock alternative rock",
        listOf("Papercut",
            "One Step Closer",
            "With You",
            "Points of Authority",
            "Crawling",
            "Runaway",
            "By Myself",
            "In the End",
            "A Place for My Head",
            "Forgotten",
            "Cure for the Itch",
            "Pushing Me Away"))

    addAlbum(
        "ALBUM_5",
        "Meteora",
        "Linkin Park",
        "Nu metal rap metal rap rock alternative rock",
        listOf("Foreword",
            "Don't Stay",
            "Somewhere I Belong",
            "Lying from You",
            "Hit the Floor",
            "Easier to Run",
            "Faint",
            "Figure.09",
            "Breaking the Habit",
            "From the Inside",
            "Nobody's Listening",
            "Session",
            "Numb"))
  }

  private fun addAlbum(
    album_token: String,
    album_title: String,
    artist_name: String,
    genre_name: String,
    tracks: List<String>
  ) {
    musicTable.albumInfo.save(AlbumInfo(
        album_token,
        album_title,
        artist_name,
        genre_name
    ))
    tracks.forEachIndexed { index, name ->
      musicTable.albumTracks.save(AlbumTrack(
          album_token,
          index.toLong(),
          name))
    }
  }
}
/*

BACKFILA + DYNAMODB NEXT STEPS


1. Migrate from Tempest API to DynamoDbMapper API
   (basically inline the work Tempest is doing in scan())

2. Use COUNT as our select on the DynamoDBScanExpression

3. Set the limit to N on the range-finding phase, and N + 10
   on the run batch phase (ie. LIMIT 10001 in VITESS)


 */