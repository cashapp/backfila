package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayer
import app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayersBackfillRecordSourceConfig
import javax.inject.Inject

class PlayerOriginBackfill @Inject constructor(
  hockeyDataDatabase: HockeyDataDatabase,
) : SqlDelightDatasourceBackfill<Int, HockeyPlayer, PlayerOriginBackfill.PlayerOriginParameters>(
  HockeyPlayersBackfillRecordSourceConfig(hockeyDataDatabase),
) {
  val backfilledPlayers = mutableListOf<HockeyPlayer>()

  override fun validate(config: PrepareBackfillConfig<PlayerOriginParameters>) {
    check(config.parameters.validate) { "Validate failed" }
  }

  override fun runOne(record: HockeyPlayer, config: BackfillConfig<PlayerOriginParameters>) {
    if (record.place_of_birth.contains(config.parameters.originRegex)) {
      backfilledPlayers.add(record)
    }
  }

  data class PlayerOriginParameters(
    val originRegex: String = "CAN",
    val validate: Boolean = true,
  )
}
