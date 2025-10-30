package app.cash.backfila.development.mcdees

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.stat.StaticDatasourceBackfill
import javax.inject.Inject
import misk.logging.getLogger

class BurgerFlippingBackfill @Inject constructor() : StaticDatasourceBackfill<String, BurgerFlippingBackfill.SlowMealsAttributes>() {
  override fun runOne(item: String, config: BackfillConfig<SlowMealsAttributes>) {
    Thread.sleep(config.parameters.waitBetweenFlipsMs())
    logger.info { "Finished flipping $item" }
  }

  data class SlowMealsAttributes(
    val flipsPerMinute: Long = 5L,
  ) {
    fun waitBetweenFlipsMs() = (1000L * 60L) / flipsPerMinute
  }

  override val staticDatasource: List<String> = (1..10000).map { i -> "burger $i" }

  companion object {
    private val logger = getLogger<BurgerFlippingBackfill>()
  }
}
