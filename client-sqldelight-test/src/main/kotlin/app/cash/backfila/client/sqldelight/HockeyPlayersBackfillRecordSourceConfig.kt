package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayer
import app.cash.sqldelight.Query

class HockeyPlayersBackfillRecordSourceConfig(
  database: HockeyDataDatabase,
) : SqlDelightRecordSourceConfig<Int, HockeyPlayer> {
  private val queries = database.allHockeyPlayersBackfillQueries
  override val keyEncoder = IntKeyEncoder
  override fun selectAbsoluteRange() = queries.selectAbsoluteRange { min, max -> MinMax(min, max) }
  override fun selectInitialMaxBound(rangeStart: Int, rangeEnd: Int, scanSize: Long): Query<NullKeyContainer<Int>> {
    return queries.selectInitialMaxBound(rangeStart, rangeEnd, scanSize) {
      NullKeyContainer(
        it,
      )
    }
  }

  override fun selectNextMaxBound(previousEndKey: Int, rangeEnd: Int, scanSize: Long): Query<NullKeyContainer<Int>> {
    return queries.selectNextMaxBound(
      previousEndKey,
      rangeEnd,
      scanSize,
    ) { NullKeyContainer(it) }
  }

  override fun produceInitialBatchFromRange(rangeStart: Int, rangeEnd: Int, offset: Long) = queries.produceInitialBatchFromRange(rangeStart, rangeEnd, offset)
  override fun produceNextBatchFromRange(previousEndKey: Int, rangeEnd: Int, offset: Long) = queries.produceNextBatchFromRange(previousEndKey, rangeEnd, offset)
  override fun countInitialBatchMatches(rangeStart: Int, boundingMax: Int) = queries.countInitialBatchMatches(rangeStart, boundingMax)
  override fun countNextBatchMatches(previousEndKey: Int, boundingMax: Int) = queries.countNextBatchMatches(previousEndKey, boundingMax)
  override fun getInitialStartKeyAndScanCount(rangeStart: Int, rangeEnd: Int) =
    queries.getInitialStartKeyAndScanCount(rangeStart, rangeEnd) { min, count ->
      MinAndCount(
        min,
        count,
      )
    }

  override fun getNextStartKeyAndScanCount(previousEndKey: Int, rangeEnd: Int) =
    queries.getNextStartKeyAndScanCount(
      previousEndKey,
      rangeEnd,
    ) { min, count -> MinAndCount(min, count) }

  override fun getBatch(start: Int, end: Int) = queries.getBatch(start, end)
}
