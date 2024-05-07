package app.cash.backfila.development.finedining

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.Description
import app.cash.backfila.client.stat.StaticDatasourceBackfill
import javax.inject.Inject
import wisp.logging.getLogger

@Description("A restaurant that takes forever to serve meals.")
class SlowMealsBackfill @Inject constructor() : StaticDatasourceBackfill<String, SlowMealsBackfill.SlowMealsAttributes>() {
  override fun runOne(item: String, config: BackfillConfig<SlowMealsAttributes>) {
    Thread.sleep(config.parameters.mealDelayMs)
    logger.info { "Finished serving $item" }
  }

  data class SlowMealsAttributes(
    val mealDelayMs: Long = 100L,
  )

  // Generate the meal place settings for the backfill.
  override val staticDatasource: List<String> = (1..5000).map { i -> "place setting $i" }

  companion object {
    private val logger = getLogger<SlowMealsBackfill>()
  }
}
