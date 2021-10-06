package app.cash.backfila.client.testing

import app.cash.backfila.embedded.internal.PartitionCursor
import org.assertj.core.api.AbstractAssert

class PartitionCursorAssert(
  private val partitionCursor: PartitionCursor
) : AbstractAssert<PartitionCursorAssert, PartitionCursor>(partitionCursor, PartitionCursorAssert::class.java) {
  fun isDone(): PartitionCursorAssert {
    if (!partitionCursor.done) {
      failWithMessage(
        "Expected the backfill partition ${partitionCursor.partitionName} " +
          "to be done but it isn't. Cursor $partitionCursor"
      )
    }
    return this // Return the current assertion for method chaining.
  }

  fun isNotDone(): PartitionCursorAssert {
    if (partitionCursor.done) {
      failWithMessage(
        "Expected the backfill partition ${partitionCursor.partitionName} " +
          "to be not done but it is. Cursor $partitionCursor"
      )
    }
    return this // Return the current assertion for method chaining.
  }
}

fun assertThat(partitionCursor: PartitionCursor): PartitionCursorAssert {
  return PartitionCursorAssert(partitionCursor)
}
