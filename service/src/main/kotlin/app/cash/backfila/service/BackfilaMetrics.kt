package app.cash.backfila.service

import javax.inject.Inject
import javax.inject.Singleton
import misk.metrics.v2.Metrics

@Singleton
class BackfilaMetrics @Inject internal constructor(metrics: Metrics) {
  val runBatchDuration = metrics.histogram(
    name = "run_batch_duration_ms",
    help = "duration of calls to runBatch in milliseconds",
    labelNames = perBackfillLabelNames,
  )

  val runBatchSuccesses = metrics.counter(
    name = "run_batch_successes_total",
    help = "number of successful runBatch calls",
    labelNames = perBackfillLabelNames,
  )

  val runBatchFailures = metrics.counter(
    name = "run_batch_failures_total",
    help = "number of failed runBatch calls",
    labelNames = perBackfillLabelNames,
  )

  val getNextBatchDuration = metrics.histogram(
    name = "get_next_batch_duration_ms",
    help = "duration of calls to getNextBatch in milliseconds",
    labelNames = perBackfillLabelNames,
  )

  val getNextBatchSuccesses = metrics.counter(
    name = "get_next_batch_successes_total",
    help = "number of successful getNextBatch calls",
    labelNames = perBackfillLabelNames,
  )

  val getNextBatchFailures = metrics.counter(
    name = "get_next_batch_failures_total",
    help = "number of failed getNextBatch calls",
    labelNames = perBackfillLabelNames,
  )

  val computedBatchCount = metrics.counter(
    name = "computed_batch_total",
    help = "number of batches computed",
    labelNames = perBackfillLabelNames,
  )

  val computedRecordsScanned = metrics.counter(
    name = "compute_batch_records_scanned_total",
    help = "number of records computed (total scanned)",
    labelNames = perBackfillLabelNames,
  )

  val computedRecordsMatching = metrics.counter(
    name = "compute_batch_records_matching_total",
    help = "number of records computed (matching)",
    labelNames = perBackfillLabelNames,
  )

  val runBatchCompletedRecordsScanned = metrics.counter(
    name = "run_batch_completed_records_scanned_total",
    help = "number of records completed (total scanned)",
    labelNames = perBackfillLabelNames,
  )

  val runBatchCompletedRecordsMatching = metrics.counter(
    name = "run_batch_completed_records_matching_total",
    help = "number of records completed (matching)",
    labelNames = perBackfillLabelNames,
  )

  val eta = metrics.gauge(
    name = "backfill_eta_ms",
    help = "Estimated remaining time for backfill completion in milliseconds based on the" +
      " records completed in the last minute",
    labelNames = perBackfillLabelNames,
  )

  val bufferedBatchesReadyToRun = metrics.gauge(
    name = "buffered_batches_ready_to_run",
    help = "The number of batches computed that are ready to be run. If this reaches 0 while the backfill is" +
      " running, there is probably a performance bottleneck in batch computing.",
    labelNames = perBackfillLabelNames,
  )

  val blockedOnComputingNextBatchDuration = metrics.histogram(
    name = "blocked_on_computing_next_batch_duration_ms",
    help = "How long the BatchRunner was blocked waiting on the BatchQueuer to compute more batches to run." +
      " If this is greater than 0 there is probably a performance bottleneck in batch computing.",
    labelNames = perBackfillLabelNames,
  )

  val batchRunsInProgress = metrics.gauge(
    name = "batch_runs_in_progress",
    help = "The number of batches that are currently running, that is, " +
      "we asked the client to start running the batch and it has not finished.",
    labelNames = perBackfillLabelNames,
  )

  companion object {
    private val perBackfillLabelNames = listOf(
      "backfill_service",
      "backfill_name",
      "backfill_id",
      "backfill_partition",
    )
  }
}
