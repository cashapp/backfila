package app.cash.backfila.client.sqldelight

import app.cash.sqldelight.Query

interface SqlDelightRecordSourceQueries<K : Any, R : Any> {
  data class MinMax<K>(
    val min: K?,
    val max: K?,
  )

  data class NullKeyContainer<K>(
    val key: K?,
  )

  data class MinAndCount<K>(
    val min: K?,
    val count: Long,
  )

  fun selectAbsoluteRange(): Query<MinMax<K>>
  fun selectInitialMaxBound(rangeStart: K, rangeEnd: K, scanSize: Long): Query<NullKeyContainer<K>>
  fun selectNextMaxBound(previousEndKey: K, rangeEnd: K, scanSize: Long): Query<NullKeyContainer<K>>
  fun produceInitialBatchFromRange(rangeStart: K, boundingMax: K, offset: Long): Query<K>
  fun produceNextBatchFromRange(previousEndKey: K, boundingMax: K, offset: Long): Query<K>
  fun countInitialBatchMatches(rangeStart: K, boundingMax: K): Query<Long>
  fun countNextBatchMatches(previousEndKey: K, boundingMax: K): Query<Long>
  fun getInitialStartKeyAndScanCount(rangeStart: K, batchEnd: K): Query<MinAndCount<K>>
  fun getNextStartKeyAndScanCount(previousEndKey: K, batchEnd: K): Query<MinAndCount<K>>
  fun getBatch(start: K, end: K): Query<R>

  companion object {
    fun <K : Any, R : Any> create(
      selectAbsoluteRange: Query<MinMax<K>>,
      selectInitialMaxBound: (rangeStart: K, rangeEnd: K, scanSize: Long) -> Query<NullKeyContainer<K>>,
      selectNextMaxBound: (previousEndKey: K, rangeEnd: K, scanSize: Long) -> Query<NullKeyContainer<K>>,
      produceInitialBatchFromRange: (rangeStart: K, boundingMax: K, offset: Long) -> Query<K>,
      produceNextBatchFromRange: (previousEndKey: K, boundingMax: K, offset: Long) -> Query<K>,
      countInitialBatchMatches: (rangeStart: K, boundingMax: K) -> Query<Long>,
      countNextBatchMatches: (previousEndKey: K, boundingMax: K) -> Query<Long>,
      getInitialStartKeyAndScanCount: (rangeStart: K, batchEnd: K) -> Query<MinAndCount<K>>,
      getNextStartKeyAndScanCount: (previousEndKey: K, batchEnd: K) -> Query<MinAndCount<K>>,
      getBatch: (start: K, end: K) -> Query<R>,
    ): SqlDelightRecordSourceQueries<K, R> {
      return object : SqlDelightRecordSourceQueries<K, R> {
        override fun selectAbsoluteRange(): Query<MinMax<K>> = selectAbsoluteRange
        override fun selectInitialMaxBound(rangeStart: K, rangeEnd: K, scanSize: Long): Query<NullKeyContainer<K>> = selectInitialMaxBound(rangeStart, rangeEnd, scanSize)
        override fun selectNextMaxBound(previousEndKey: K, rangeEnd: K, scanSize: Long): Query<NullKeyContainer<K>> = selectNextMaxBound(previousEndKey, rangeEnd, scanSize)
        override fun produceInitialBatchFromRange(rangeStart: K, boundingMax: K, offset: Long): Query<K> = produceInitialBatchFromRange(rangeStart, boundingMax, offset)
        override fun produceNextBatchFromRange(previousEndKey: K, boundingMax: K, offset: Long): Query<K> = produceNextBatchFromRange(previousEndKey, boundingMax, offset)
        override fun countInitialBatchMatches(rangeStart: K, boundingMax: K): Query<Long> = countInitialBatchMatches(rangeStart, boundingMax)
        override fun countNextBatchMatches(previousEndKey: K, boundingMax: K): Query<Long> = countNextBatchMatches(previousEndKey, boundingMax)
        override fun getInitialStartKeyAndScanCount(rangeStart: K, batchEnd: K): Query<MinAndCount<K>> = getInitialStartKeyAndScanCount(rangeStart, batchEnd)
        override fun getNextStartKeyAndScanCount(previousEndKey: K, batchEnd: K): Query<MinAndCount<K>> = getNextStartKeyAndScanCount(previousEndKey, batchEnd)
        override fun getBatch(start: K, end: K): Query<R> = getBatch(start, end)
      }
    }
  }
}
