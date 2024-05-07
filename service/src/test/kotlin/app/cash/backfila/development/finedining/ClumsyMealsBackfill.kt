package app.cash.backfila.development.finedining

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.Description
import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.stat.StaticDatasourceBackfill
import javax.inject.Inject
import wisp.logging.getLogger

@Description("A very clumsy restaurant that keeps breaking plates.")
class ClumsyMealsBackfill @Inject constructor() : StaticDatasourceBackfill<String, ClumsyMealsBackfill.ClumsyMealsAttributes>() {
  var servedPlates = 0 // Keeps track of the number of successfully served plates.
  var brokenPlatesSoFar = 0 // When plates break it keeps track of how many have broken so far.
  override fun validate(config: PrepareBackfillConfig<ClumsyMealsAttributes>) {
    if (config.parameters.blockedEntrance) {
      throw RuntimeException("Entrance is BLOCKED. No customers can be served!")
    }
  }

  override fun runOne(item: String, config: BackfillConfig<ClumsyMealsAttributes>) {
    Thread.sleep(100L) // Sleep for half a second
    // We potentially break on every 25th plate.
    if (servedPlates % 25 == 0 && brokenPlatesSoFar < config.parameters.brokenPlates) {
      brokenPlatesSoFar++
      logger.info { "Broke a plate!" }
      throw RuntimeException("Failed to serve: Broke a plate!!!")
    }
    logger.info { "Poorly served $item" }
    servedPlates++
    brokenPlatesSoFar = 0
  }

  @Description("Adaptable clumsiness.")
  data class ClumsyMealsAttributes(
    @Description("Whether the entrance is blocked or not. Can be used to test Prepare failures.")
    val blockedEntrance: Boolean = true,
    @Description("How many plates break before a successful plate is served. Can be used to test retry logic.")
    val brokenPlates: Int = 1,
  )

  // Generate the meal place settings for the backfill.
  override val staticDatasource: List<String> = (1..5000).map { i -> "place setting $i" }

  companion object {
    private val logger = getLogger<ClumsyMealsBackfill>()
  }
}
