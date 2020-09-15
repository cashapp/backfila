package app.cash.backfila.service.runner.statemachine

import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.RunBatchResponse
import java.time.Instant
import kotlinx.coroutines.Deferred

data class AwaitingRun(
  val batch: GetNextBatchRangeResponse.Batch,
  val runBatchRpc: Deferred<RunBatchResponse>,
  val startedAt: Instant
)
