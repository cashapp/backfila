package app.cash.backfila.development.mcdees

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.s3.S3DatasourceBackfill
import app.cash.backfila.client.s3.record.RecordStrategy
import app.cash.backfila.client.s3.record.Utf8StringNewlineStrategy
import javax.inject.Inject
import wisp.logging.getLogger

class RestockingBackfill @Inject constructor() : S3DatasourceBackfill<String, RestockingBackfill.RestockingAttributes>() {
  override fun runOne(item: String, config: BackfillConfig<RestockingAttributes>) {
    logger.info { "Finished flipping $item" }
  }

  override fun getBucket(config: PrepareBackfillConfig<RestockingAttributes>): String {
    return "restocking"
  }

  override fun getPrefix(config: PrepareBackfillConfig<RestockingAttributes>): String {
    return config.parameters.getPrefix()
  }

  override val recordStrategy: RecordStrategy<String> = Utf8StringNewlineStrategy(ignoreBlankLines = false)

  class RestockingAttributes(restockingType: String, val particularSupplier: String? = null) {
    val restockingType: RestockingType
    init {
      this.restockingType = RestockingType.valueOf(restockingType)
    }

    fun getPrefix(): String {
      return when (restockingType) {
        RestockingType.ALL -> ""
        RestockingType.FOOD -> "food"
        RestockingType.HARD_GOODS -> "hardgoods"
        RestockingType.SPECIFIC_SUPPLIER -> ""
      }
    }
  }

  enum class RestockingType { ALL, FOOD, HARD_GOODS, SPECIFIC_SUPPLIER }

  companion object {
    private val logger = getLogger<RestockingBackfill>()
  }
}
