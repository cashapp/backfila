package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayer
import javax.inject.Inject

class PlayerOriginBackfill @Inject constructor(
  val hockeyDataDatabase: HockeyDataDatabase,
) : SqlDelightDatasourceBackfill<SqlDelightRecordSource<Int, HockeyPlayer>, Int, HockeyPlayer, PlayerOriginBackfill.PlayerOriginParameters>(
  hockeyPlayerRowSource(hockeyDataDatabase),
) {
  val backfilledPlayers = mutableListOf<Pair<String, HockeyPlayer>>()

  override fun validate(config: PrepareBackfillConfig<PlayerOriginParameters>) {
    check(config.parameters.validate) { "Validate failed" }
  }

  override fun runOne(record: HockeyPlayer, config: BackfillConfig<PlayerOriginParameters>) {
    if (record.place_of_birth.contains(config.parameters.originRegex)) {
      backfilledPlayers.add(config.partitionName to record)
    }
  }

  data class PlayerOriginParameters(
    val originRegex: String = "CAN",
    val validate: Boolean = true,
  )
}

fun hockeyPlayerRowSource(hockeyDataDatabase: HockeyDataDatabase) = SqlDelightRecordSource(
  IntKeyConverter(),
  getHockeyPlayersBackfillQueries(hockeyDataDatabase),
)
