package app.cash.backfila.client.sqldelight

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for Vitess bounding range strategy utilities.
 *
 * Note: Full integration tests for VitessShardedPartitionProvider and
 * VitessSingleCursorPartitionProvider require a Vitess database.
 * These tests verify the utility functions used by the strategies.
 */
class VitessPartitionProviderTest {

  @Test
  fun `minimum of comparable values works correctly`() {
    // This tests the min logic used by VitessSingleCursorBoundingRangeStrategy
    val values: List<Long> = listOf(100L, 50L, 200L, 75L)

    @Suppress("UNCHECKED_CAST")
    val min = (values as List<Comparable<Any>>).minWithOrNull(naturalOrder()) as Long?

    assertThat(min).isEqualTo(50L)
  }

  @Test
  fun `minimum of empty list returns null`() {
    val values: List<Long> = emptyList()

    @Suppress("UNCHECKED_CAST")
    val min = (values as List<Comparable<Any>>).minWithOrNull(naturalOrder()) as Long?

    assertThat(min).isNull()
  }

  @Test
  fun `minimum of single value returns that value`() {
    val values: List<Long> = listOf(42L)

    @Suppress("UNCHECKED_CAST")
    val min = (values as List<Comparable<Any>>).minWithOrNull(naturalOrder()) as Long?

    assertThat(min).isEqualTo(42L)
  }

  @Test
  fun `SqlDelightRecordSourceConfig tableName defaults to null`() {
    // Verify the default implementation returns null for backwards compatibility
    val config = object : SqlDelightRecordSourceConfig<Long, String> {
      override val keyEncoder = LongKeyEncoder
      override fun selectAbsoluteRange() = throw NotImplementedError()
      override fun selectInitialMaxBound(rangeStart: Long, rangeEnd: Long, scanSize: Long) = throw NotImplementedError()
      override fun selectNextMaxBound(previousEndKey: Long, rangeEnd: Long, scanSize: Long) = throw NotImplementedError()
      override fun produceInitialBatchFromRange(rangeStart: Long, boundingMax: Long, offset: Long) = throw NotImplementedError()
      override fun produceNextBatchFromRange(previousEndKey: Long, boundingMax: Long, offset: Long) = throw NotImplementedError()
      override fun countInitialBatchMatches(rangeStart: Long, boundingMax: Long) = throw NotImplementedError()
      override fun countNextBatchMatches(previousEndKey: Long, boundingMax: Long) = throw NotImplementedError()
      override fun getInitialStartKeyAndScanCount(rangeStart: Long, batchEnd: Long) = throw NotImplementedError()
      override fun getNextStartKeyAndScanCount(previousEndKey: Long, batchEnd: Long) = throw NotImplementedError()
      override fun getBatch(start: Long, end: Long) = throw NotImplementedError()
    }

    // Default implementations should return null for backwards compatibility
    assertThat(config.tableName()).isNull()
    assertThat(config.primaryKeyColumn()).isNull()
  }

  @Test
  fun `SqlDelightRecordSourceConfig can override tableName and primaryKeyColumn`() {
    val config = object : SqlDelightRecordSourceConfig<Long, String> {
      override val keyEncoder = LongKeyEncoder
      override fun tableName() = "my_table"
      override fun primaryKeyColumn() = "id"
      override fun selectAbsoluteRange() = throw NotImplementedError()
      override fun selectInitialMaxBound(rangeStart: Long, rangeEnd: Long, scanSize: Long) = throw NotImplementedError()
      override fun selectNextMaxBound(previousEndKey: Long, rangeEnd: Long, scanSize: Long) = throw NotImplementedError()
      override fun produceInitialBatchFromRange(rangeStart: Long, boundingMax: Long, offset: Long) = throw NotImplementedError()
      override fun produceNextBatchFromRange(previousEndKey: Long, boundingMax: Long, offset: Long) = throw NotImplementedError()
      override fun countInitialBatchMatches(rangeStart: Long, boundingMax: Long) = throw NotImplementedError()
      override fun countNextBatchMatches(previousEndKey: Long, boundingMax: Long) = throw NotImplementedError()
      override fun getInitialStartKeyAndScanCount(rangeStart: Long, batchEnd: Long) = throw NotImplementedError()
      override fun getNextStartKeyAndScanCount(previousEndKey: Long, batchEnd: Long) = throw NotImplementedError()
      override fun getBatch(start: Long, end: Long) = throw NotImplementedError()
    }

    assertThat(config.tableName()).isEqualTo("my_table")
    assertThat(config.primaryKeyColumn()).isEqualTo("id")
  }
}
