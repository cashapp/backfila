package app.cash.backfila.client.sqldelight

import HockeyPlayer
import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import com.squareup.wire.internal.newMutableList
import javax.inject.Inject

class PlayerOriginBackfill @Inject constructor(
  val hockeyDataDatabase: HockeyDataDatabase,
) : SqlDelightDatasourceBackfill<SqlDelightRowSource<Int, HockeyPlayer>, Int, HockeyPlayer, PlayerOriginBackfill.PlayerOriginParameters>(
  SqlDelightRowSource(
    hockeyDataDatabase,
    IntKeyConverter(),
    hockeyDataDatabase.allHockeyPlayersBackfillQueries.selectOverallRange { min, max -> SqlDelightRowSource.MinMax(min, max) },
    { rangeStart: Int, rangeEnd: Int, scanSize: Long -> hockeyDataDatabase.allHockeyPlayersBackfillQueries.selectInitialMaxBound(rangeStart, rangeEnd, scanSize) { SqlDelightRowSource.NullKeyContainer(it) } },
    { previousEndKey: Int, rangeEnd: Int, scanSize: Long -> hockeyDataDatabase.allHockeyPlayersBackfillQueries.selectNextMaxBound(previousEndKey, rangeEnd, scanSize) { SqlDelightRowSource.NullKeyContainer(it) } },
    { rangeStart: Int, rangeEnd: Int, offset: Long -> hockeyDataDatabase.allHockeyPlayersBackfillQueries.produceInitialBatchFromRange(rangeStart, rangeEnd, offset) },
    { previousEndKey: Int, rangeEnd: Int, offset: Long -> hockeyDataDatabase.allHockeyPlayersBackfillQueries.produceNextBatchFromRange(previousEndKey, rangeEnd, offset) },
    { rangeStart: Int, boundingMax: Int -> hockeyDataDatabase.allHockeyPlayersBackfillQueries.countInitialBatchMatches(rangeStart, boundingMax) },
    { previousEndKey: Int, boundingMax: Int -> hockeyDataDatabase.allHockeyPlayersBackfillQueries.countNextBatchMatches(previousEndKey, boundingMax) },
    { rangeStart: Int, rangeEnd: Int -> hockeyDataDatabase.allHockeyPlayersBackfillQueries.getInitialStartKeyAndScanCount(rangeStart, rangeEnd) { min, count -> SqlDelightRowSource.MinAndCount(min, count) } },
    { previousEndKey: Int, rangeEnd: Int -> hockeyDataDatabase.allHockeyPlayersBackfillQueries.getNextStartKeyAndScanCount(previousEndKey, rangeEnd) { min, count -> SqlDelightRowSource.MinAndCount(min, count) } },
    { start: Int, end: Int -> hockeyDataDatabase.allHockeyPlayersBackfillQueries.getBatch(start, end) },
  ),
) {
  val backfilledPlayers = newMutableList<Pair<String, HockeyPlayer>>()

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
