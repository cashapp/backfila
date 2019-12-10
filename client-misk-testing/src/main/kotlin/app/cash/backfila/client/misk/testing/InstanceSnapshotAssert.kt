package app.cash.backfila.client.misk.testing

import app.cash.backfila.client.misk.internal.InstanceCursor
import org.assertj.core.api.AbstractAssert

class InstanceCursorAssert(
  val instanceCursor: InstanceCursor
) : AbstractAssert<InstanceCursorAssert, InstanceCursor>(instanceCursor, InstanceCursorAssert::class.java) {
  fun isDone(): InstanceCursorAssert {
    if (!instanceCursor.done) {
      failWithMessage("Expected the backfill instance ${instanceCursor.instanceName} " +
          "to be done but it isn't. Cursor $instanceCursor")
    }
    return this // Return the current assertion for method chaining.
  }

  fun isNotDone(): InstanceCursorAssert {
    if (instanceCursor.done) {
      failWithMessage("Expected the backfill instance ${instanceCursor.instanceName} " +
          "to be not done but it is. Cursor $instanceCursor")
    }
    return this // Return the current assertion for method chaining.
  }
}

fun assertThat(instanceCursor: InstanceCursor): InstanceCursorAssert {
  return InstanceCursorAssert(instanceCursor)
}
