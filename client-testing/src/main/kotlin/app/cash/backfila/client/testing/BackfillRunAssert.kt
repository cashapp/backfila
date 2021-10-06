package app.cash.backfila.client.testing

import app.cash.backfila.client.Backfill
import app.cash.backfila.embedded.BackfillRun
import org.assertj.core.api.AbstractAssert

class BackfillRunAssert(
  private val backfillRun: BackfillRun<*>
) : AbstractAssert<BackfillRunAssert, BackfillRun<*>>(backfillRun, BackfillRunAssert::class.java) {

  fun isFinishedPrecomputing(): BackfillRunAssert {
    if (!backfillRun.finishedPrecomputing()) {
      failWithMessage(
        "Expected the backfill run of type ${backfillRun.backfill.javaClass.simpleName} " +
          "to be finished precomputing but it isn't."
      )
    }
    return this // Return the current assertion for method chaining.
  }

  fun isFinishedScanning(): BackfillRunAssert {
    if (!backfillRun.finishedScanning()) {
      failWithMessage(
        "Expected the backfill run of type ${backfillRun.backfill.javaClass.simpleName} " +
          "to be finished scanning but it isn't."
      )
    }
    return this // Return the current assertion for method chaining.
  }

  fun hasNoBatchesToRun(): BackfillRunAssert {
    if (backfillRun.batchesToRunSnapshot.isNotEmpty()) {
      failWithMessage(
        "Expected the backfill run of type ${backfillRun.backfill.javaClass.simpleName} " +
          "to have no batches to run but it has ${backfillRun.batchesToRunSnapshot.size} pending batches."
      )
    }
    return this // Return the current assertion for method chaining.
  }

  fun hasBatchesToRun(): BackfillRunAssert {
    if (backfillRun.batchesToRunSnapshot.isEmpty()) {
      failWithMessage(
        "Expected the backfill run of type ${backfillRun.backfill.javaClass.simpleName} " +
          "to have batches to run but there are no pending batches."
      )
    }
    return this // Return the current assertion for method chaining.
  }

  fun isComplete(): BackfillRunAssert {
    if (!backfillRun.complete()) {
      failWithMessage(
        "Expected the backfill run of type ${backfillRun.backfill.javaClass.simpleName} " +
          "to be complete but it isn't. finished scanning? ${backfillRun.finishedScanning()} , " +
          "batches left to run ${backfillRun.batchesToRunSnapshot.size}"
      )
    }
    return this // Return the current assertion for method chaining.
  }

  fun isNotComplete(): BackfillRunAssert {
    if (backfillRun.complete()) {
      failWithMessage(
        "Expected the backfill run of type ${backfillRun.backfill.javaClass.simpleName} " +
          "to be incomplete but it is done. finished scanning? ${backfillRun.finishedScanning()} , " +
          "batches left to run ${backfillRun.batchesToRunSnapshot.size}"
      )
    }
    return this // Return the current assertion for method chaining.
  }
}

fun <T : Backfill> assertThat(backfillRun: BackfillRun<T>): BackfillRunAssert {
  return BackfillRunAssert(backfillRun)
}
