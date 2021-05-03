package app.cash.backfila.service

import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest

/**
 * Same as runBlocking but cancels the coroutines if an exception is thrown.
 * runBlockingTest has a bug where it hangs instead:
 * https://github.com/Kotlin/kotlinx.coroutines/issues/1910
 */
@Suppress("EXPERIMENTAL_API_USAGE")
internal fun runBlockingTestCancellable(
  testBody: suspend TestCoroutineScope.() -> Unit
) {
  runBlockingTest {
    try {
      this.testBody()
    } catch (t: Throwable) {
      coroutineContext.cancel()
      throw t
    }
  }
}
