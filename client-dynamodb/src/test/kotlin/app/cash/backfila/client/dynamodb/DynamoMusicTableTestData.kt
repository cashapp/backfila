package app.cash.backfila.client.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import javax.inject.Inject

class DynamoMusicTableTestData @Inject constructor(
  var dynamoDb: DynamoDBMapper
) {

  fun getTracksDump(): List<TrackItem> {
    return getRowsDump().filter { it.sort_key?.startsWith("TRACK_") == true }
  }

  fun getRowsDump(): List<TrackItem> {
    val scanRequest = DynamoDBScanExpression().apply {
      limit = 10000
    }
    return dynamoDb.scan(TrackItem::class.java, scanRequest)
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
        "Burn This Disco Out"
      )
    )
  }

  fun addThriller() {
    addAlbum(
      "ALBUM_2",
      "Thriller",
      "Michael Jackson",
      listOf(
        "Wanna Be Startin' Somethin'",
        "Baby Be Mine",
        "The Girl Is Mine",
        "Thriller",
        "Beat It",
        "Billie Jean",
        "Human Nature",
        "P.Y.T. (Pretty Young Thing)",
        "The Lady in My Life"
      )
    )
  }

  fun addMichaelBuble() {
    addAlbum(
      "ALBUM_3",
      "Michael Bublé",
      "Michael Bublé",
      listOf(
        "Fever",
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
        "That's All"
      )
    )
  }

  fun addLinkinPark() {
    addAlbum(
      "ALBUM_4",
      "Hybrid Theory",
      "Linkin Park",
      listOf(
        "Papercut",
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
        "Pushing Me Away"
      )
    )

    addAlbum(
      "ALBUM_5",
      "Meteora",
      "Linkin Park",
      listOf(
        "Foreword",
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
        "Numb"
      )
    )
  }

  private fun addAlbum(
    album_token: String,
    album_title: String,
    artist_name: String,
    tracks: List<String>
  ) {
    // Insert the album info
    val albumItem = TrackItem().apply {
      this.album_token = album_token
      this.sort_key = "INFO_"
      this.album_title = album_title
      this.artist_name = artist_name
    }
    dynamoDb.save(albumItem)
    // Insert the tracks info
    tracks.forEachIndexed { index, name ->
      val trackItem = TrackItem().apply {
        this.album_token = album_token
        this.sort_key = "TRACK_%016x".format(index)
        this.track_title = name
      }
      dynamoDb.save(trackItem)
    }
  }
}
