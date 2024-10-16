package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayer

fun getHockeyPlayersBackfillQueries(database: HockeyDataDatabase): SqlDelightRecordSourceQueries<Int, HockeyPlayer> = SqlDelightRecordSourceQueries.create(
  database.allHockeyPlayersBackfillQueries.selectAbsoluteRange { min, max -> SqlDelightRecordSourceQueries.MinMax(min, max) },
  { rangeStart: Int, rangeEnd: Int, scanSize: Long ->
    database.allHockeyPlayersBackfillQueries.selectInitialMaxBound(rangeStart, rangeEnd, scanSize) {
      SqlDelightRecordSourceQueries.NullKeyContainer(
        it,
      )
    }
  },
  { previousEndKey: Int, rangeEnd: Int, scanSize: Long ->
    database.allHockeyPlayersBackfillQueries.selectNextMaxBound(
      previousEndKey,
      rangeEnd,
      scanSize,
    ) { SqlDelightRecordSourceQueries.NullKeyContainer(it) }
  },
  { rangeStart: Int, rangeEnd: Int, offset: Long -> database.allHockeyPlayersBackfillQueries.produceInitialBatchFromRange(rangeStart, rangeEnd, offset) },
  { previousEndKey: Int, rangeEnd: Int, offset: Long -> database.allHockeyPlayersBackfillQueries.produceNextBatchFromRange(previousEndKey, rangeEnd, offset) },
  { rangeStart: Int, boundingMax: Int -> database.allHockeyPlayersBackfillQueries.countInitialBatchMatches(rangeStart, boundingMax) },
  { previousEndKey: Int, boundingMax: Int -> database.allHockeyPlayersBackfillQueries.countNextBatchMatches(previousEndKey, boundingMax) },
  { rangeStart: Int, rangeEnd: Int ->
    database.allHockeyPlayersBackfillQueries.getInitialStartKeyAndScanCount(rangeStart, rangeEnd) { min, count ->
      SqlDelightRecordSourceQueries.MinAndCount(
        min,
        count,
      )
    }
  },
  { previousEndKey: Int, rangeEnd: Int ->
    database.allHockeyPlayersBackfillQueries.getNextStartKeyAndScanCount(
      previousEndKey,
      rangeEnd,
    ) { min, count -> SqlDelightRecordSourceQueries.MinAndCount(min, count) }
  },
  { start: Int, end: Int -> database.allHockeyPlayersBackfillQueries.getBatch(start, end) },
)
