package app.cash.backfila.client.misk

import app.cash.backfila.client.Backfill
import com.google.inject.Injector
import jakarta.inject.Inject
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

data class BackfillInstanceMetadata(
  val qualifiedName: String,
  val type: String,
)

data class BackfillMetadata(
  val backfills: List<BackfillInstanceMetadata>,
) : Metadata(
  metadata = backfills,
  prettyPrint = defaultKotlinMoshi
    .adapter<List<BackfillInstanceMetadata>>()
    .toFormattedJson(backfills),
  descriptionString = "Backfill classes registered with Backfila.",
)

class BackfillMetadataProvider : MetadataProvider<BackfillMetadata> {
  @Inject lateinit var injector: Injector

  override val id = "backfila"

  override fun get(): BackfillMetadata {
    val backfillMetadata = backfills.map {
      BackfillInstanceMetadata(
        qualifiedName = it::class.qualifiedName!!,
        type = it::class.supertypes.joinToString { ", " },
      )
    }
    return BackfillMetadata(backfillMetadata)
  }
}
