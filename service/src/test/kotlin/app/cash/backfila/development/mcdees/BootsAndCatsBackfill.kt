package app.cash.backfila.development.mcdees

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.stat.StaticDatasourceBackfill
import jakarta.inject.Inject
import misk.logging.getLogger

/**
 * Inspired by https://www.youtube.com/watch?v=Nni0rTLg5B8
 */
class BootsAndCatsBackfill @Inject constructor() : StaticDatasourceBackfill<String, BootsAndCatsBackfill.SlowMealsAttributes>() {
  override fun runOne(item: String, config: BackfillConfig<SlowMealsAttributes>) {
    if (config.parameters.eatMoreChikin and item.contains("beef", true)) throw RuntimeException("Eat More Chikin")
    Thread.sleep(config.parameters.waitBetweenHalfBeatsMs())
    logger.info { "Rocking to $item" }
  }

  data class SlowMealsAttributes(
    val beatsPerMinute: Long = 120L,
    val eatMoreChikin: Boolean = false,
  ) {
    fun waitBetweenHalfBeatsMs() = (1000L * 60L) / (beatsPerMinute * 2)
  }

  override val staticDatasource: List<String> =
    listOf("🥾boot", " ", "🐈cat", "") + bootsAndCatsAnd(15) +
      (1..10).map { listOf("🐝bee") }.flatten() +
      listOf("🐝BEE", "🐝EEE", "🐝EEE", "🐝EEE", "🐝SSS🐝", "🐝ZZZ🐝", "🐝ZZZ🐝", "🐝ZZZ🐝") +
      bootsAndCatsAnd(10) +
      listOf("🦬bi", "son🦬", "beef🥩", "and") +
      bootsAndCatsAnd(5)

  fun bootsAndCatsAnd(times: Int) = (1..times).map { listOf("👢boots👢", "and", "🐈cats🐈‍⬛", "and") }.flatten()

  companion object {
    private val logger = getLogger<BootsAndCatsBackfill>()
  }
}
