package app.cash.backfila.actions

import app.cash.backfila.dashboard.UiPartition
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.ui.actions.BackfillCreateHandlerAction.Companion.continueRange
import app.cash.backfila.ui.actions.BackfillCreateHandlerAction.Companion.restartRange
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * A clone carries one range for every partition, so these cover which source positions can be
 * expressed as a single range and which cannot.
 */
class BackfillCloneRangeTest {
  @Test
  fun `continuing a single partition uses its cursor`() {
    val range = continueRange(listOf(partition("only", cursor = "500", start = "1", end = "9000")))

    assertThat(range.start).isEqualTo("500")
    assertThat(range.end).isEqualTo("9000")
  }

  @Test
  fun `continuing a single partition that never started uses its range start`() {
    val range = continueRange(listOf(partition("only", cursor = null, start = "1", end = "9000")))

    assertThat(range.start).isEqualTo("1")
    assertThat(range.end).isEqualTo("9000")
  }

  @Test
  fun `continuing refuses more than one partition`() {
    val partitions = listOf(
      partition("shard-1", cursor = "500", start = "1", end = "9000"),
      partition("shard-2", cursor = "7000", start = "1", end = "9000"),
    )

    assertThatThrownBy { continueRange(partitions) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Cannot continue a backfill that has 2 partitions")
  }

  @Test
  fun `restarting carries a range every partition shares`() {
    val partitions = listOf(
      partition("shard-1", cursor = "500", start = "1", end = "9000"),
      partition("shard-2", cursor = "7000", start = "1", end = "9000"),
    )

    val range = restartRange(partitions)

    assertThat(range.start).isEqualTo("1")
    assertThat(range.end).isEqualTo("9000")
  }

  @Test
  fun `restarting drops a range that differs between partitions`() {
    val partitions = listOf(
      partition("shard-1", cursor = "500", start = "1", end = "9000"),
      partition("shard-2", cursor = "7000", start = "1", end = "17701296"),
    )

    val range = restartRange(partitions)

    assertThat(range.start).isNull()
    assertThat(range.end).isNull()
  }

  @Test
  fun `restarting a single partition carries its range`() {
    val range = restartRange(listOf(partition("only", cursor = "500", start = "1", end = "9000")))

    assertThat(range.start).isEqualTo("1")
    assertThat(range.end).isEqualTo("9000")
  }

  private fun partition(
    name: String,
    cursor: String?,
    start: String?,
    end: String?,
  ) = UiPartition(
    id = 1L,
    name = name,
    state = BackfillState.PAUSED,
    pkey_cursor = cursor,
    pkey_start = start,
    pkey_end = end,
    precomputing_done = false,
    precomputing_pkey_cursor = null,
    computed_scanned_record_count = 0,
    computed_matching_record_count = 0,
    backfilled_scanned_record_count = 0,
    backfilled_matching_record_count = 0,
    scanned_records_per_minute = null,
    matching_records_per_minute = null,
  )
}
