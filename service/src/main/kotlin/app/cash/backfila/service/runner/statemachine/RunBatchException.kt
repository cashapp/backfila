package app.cash.backfila.service.runner.statemachine

class RunBatchException(
  /** Stacktrace propogated by the client, useful for displaying to the user. */
  val stackTrace: String,
) : Exception()
