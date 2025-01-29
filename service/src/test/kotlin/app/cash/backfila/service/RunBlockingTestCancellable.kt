package app.cash.backfila.service

import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Same as runBlocking but cancels the coroutines if an exception is thrown.
 * runTest has a bug where it hangs instead:
 * https://github.com/Kotlin/kotlinx.coroutines/issues/1910
 */
@Suppress("EXPERIMENTAL_API_USAGE")
internal fun runTestCancellable(
  testBody: suspend TestScope.() -> Unit,
) {
  runTest {
    try {
      this.testBody()
    } catch (t: Throwable) {
      coroutineContext.cancel()
      throw t
    }
  }
}
