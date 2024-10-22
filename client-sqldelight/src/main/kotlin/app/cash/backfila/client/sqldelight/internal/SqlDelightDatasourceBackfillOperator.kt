package app.cash.backfila.client.sqldelight.internal

import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.sqldelight.SqlDelightDatasourceBackfill
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse.Batch
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.base.Preconditions.checkArgument
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import java.util.concurrent.TimeUnit

class SqlDelightDatasourceBackfillOperator<K : Any, R : Any, P : Any>(
  override val backfill: SqlDelightDatasourceBackfill<K, R, P>,
  private val parametersOperator: BackfilaParametersOperator<P>,
) : BackfillOperator {
  private val recSourceConfig = backfill.recordSourceConfig
  private val recordSource = SqlDelightRecordSource(recSourceConfig)

  override fun name(): String = backfill.javaClass.toString()

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    recordSource.validateRange(request.range)

    backfill.validate(
      parametersOperator.constructBackfillConfig(request),
    )

    // TODO(mikepaw) What is the sharding strategy here? How to have more than just `only`?
    // partitionProvider.names(request).map { partitionForShard(it, request.range) }

    val partitionMap = mapOf("only" to "transacter or what to do with SqlDelight when there are multiple?")

    val partitions = partitionMap.map {
      PrepareBackfillResponse.Partition.Builder()
        .partition_name(it.key)
        .backfill_range(
          recordSource.computeOverallRange(request.range),
        ).build()
    }
    return PrepareBackfillResponse.Builder()
      .partitions(partitions)
      .build()
  }

  override fun getNextBatchRange(
    request: GetNextBatchRangeRequest,
  ): GetNextBatchRangeResponse {
    checkArgument(request.compute_count_limit > 0, "batch limit must be > 0")
    if (request.backfill_range.start == null) {
      // This partition never had any data, stop it immediately.
      return GetNextBatchRangeResponse.Builder()
        .batches(ImmutableList.of())
        .build()
    }

    val stopwatch = Stopwatch.createStarted()
    val batchGenerator = recordSource.getBatchGenerator(request)

    val batches = mutableListOf<Batch>()
    while (batches.size < request.compute_count_limit) {
      val batch = batchGenerator.next() ?: break // No more batches can be computed.

      batches.add(batch)

      if (request.compute_time_limit_ms != null &&
        stopwatch.elapsed(TimeUnit.MILLISECONDS) > request.compute_time_limit_ms
      ) {
        break
      }
    }

    return GetNextBatchRangeResponse.Builder()
      .batches(batches)
      .build()
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    val config = parametersOperator.constructBackfillConfig(
      request,
    )

    val batch = recordSource.getBatchData(request)
    backfill.runBatch(batch, config)

    return RunBatchResponse.Builder().build()
  }
}
