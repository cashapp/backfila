package app.cash.backfila.client.misk

import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillRegistration
import jakarta.inject.Inject
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.adapter
import wisp.moshi.defaultKotlinMoshi

internal data class BackfillMetadata(
  val name: String,
  val description: String?,
  val parametersClass: String?,
)

internal data class BackfillsMetadata(
  val backfills: Map<String, List<BackfillMetadata>>,
) : Metadata(
  metadata = backfills,
  prettyPrint = defaultKotlinMoshi
    .adapter<Map<String, List<BackfillMetadata>>>()
    .toFormattedJson(backfills),
  descriptionString = "Backfill classes registered with Backfila.",
)

internal class BackfillMetadataProvider : MetadataProvider<BackfillsMetadata> {
  @Inject lateinit var backends: Set<BackfillBackend>

  override val id = "backfila"

  override fun get(): BackfillsMetadata {
    val backfills = backends.associate { it::class.simpleName!! to it.backfills().map { it.toMetadata() } }
    return BackfillsMetadata(backfills)
  }

  private fun BackfillRegistration.toMetadata() = BackfillMetadata(
    name = name,
    description = description,
    parametersClass = parametersClass.simpleName,
  )
}
