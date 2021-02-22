package app.cash.backfila.service

import javax.inject.Inject
import javax.inject.Singleton
import misk.metrics.Metrics

@Singleton
class BackfilaMetrics @Inject internal constructor(metrics: Metrics) {
  val runBatchDuration = metrics.histogram(
    name = "run_batch_duration",
    help = "duration of calls to runBatch in milliseconds",
    labelNames = perBackfillLabelNames
  )

  val runBatchSuccesses = metrics.counter(
    name = "run_batch_successes",
    help = "number of successful runBatch calls",
    labelNames = perBackfillLabelNames
  )

  val runBatchFailures = metrics.counter(
    name = "run_batch_failures",
    help = "number of failed runBatch calls",
    labelNames = perBackfillLabelNames
  )

  val getNextBatchDuration = metrics.histogram(
    name = "get_next_batch_duration",
    help = "duration of calls to getNextBatch in milliseconds",
    labelNames = perBackfillLabelNames
  )

  val getNextBatchSuccesses = metrics.counter(
    name = "get_next_batch_successes",
    help = "number of successful getNextBatch calls",
    labelNames = perBackfillLabelNames
  )

  val getNextBatchFailures = metrics.counter(
    name = "get_next_batch_failures",
    help = "number of failed getNextBatch calls",
    labelNames = perBackfillLabelNames
  )

  val computedBatchCount = metrics.counter(
    name = "computed_batch_count",
    help = "number of batches computed",
    labelNames = perBackfillLabelNames
  )

  val computedRecordsScanned = metrics.counter(
    name = "compute_batch_records_scanned",
    help = "number of records computed (total scanned)",
    labelNames = perBackfillLabelNames
  )

  val computedRecordsMatching = metrics.counter(
    name = "compute_batch_records_matching",
    help = "number of records computed (matching)",
    labelNames = perBackfillLabelNames
  )

  val runBatchCompletedRecordsScanned = metrics.counter(
    name = "run_batch_completed_records_scanned",
    help = "number of records completed (total scanned)",
    labelNames = perBackfillLabelNames
  )

  val runBatchCompletedRecordsMatching = metrics.counter(
    name = "run_batch_completed_records_matching",
    help = "number of records completed (matching)",
    labelNames = perBackfillLabelNames
  )

  val eta = metrics.gauge(
    name = "backfill_eta",
    help = "Estimated remaining time for backfill completion in milliseconds based on the" +
        " records completed in the last minute",
    labelNames = perBackfillLabelNames
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
