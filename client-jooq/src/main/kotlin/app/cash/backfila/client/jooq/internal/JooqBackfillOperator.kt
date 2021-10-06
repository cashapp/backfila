package app.cash.backfila.client.jooq.internal

import app.cash.backfila.client.jooq.BackfillBatch
import app.cash.backfila.client.jooq.CompoundKeyComparer
import app.cash.backfila.client.jooq.JooqBackfill
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse.Partition
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.collect.Streams
import okio.ByteString
import java.util.stream.Collectors

/**
 * @param <K> the type of key the backfill iterates over.
 * @param <Param> backfill parameters
 */
class JooqBackfillOperator<K, Param : Any> internal constructor(
  override val backfill: JooqBackfill<K, Param>,
  private val parametersOperator: BackfilaParametersOperator<Param>
) : BackfillOperator {

  override fun name(): String = backfill.javaClass.name

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    backfill.prepareAndValidateBackfill(
      parametersOperator.constructBackfillConfig(
        request.parameters, request.dry_run
      )
    )

    val partitions = backfill.shardedTransacterMap.keys.map { partitionName ->
      Partition.Builder()
        .partition_name(partitionName)
        .backfill_range(computeOverallRange(partitionName, request))
        .estimated_record_count(null)
        .build()
    }

    return PrepareBackfillResponse.Builder().partitions(partitions).build()
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    check(request.compute_count_limit > 0) { "batch limit must be > 0" }

    if (request.backfill_range.start == null) {
      // This partition never had any data, stop it immediately.
      return GetNextBatchRangeResponse.Builder()
        .batches(emptyList())
        .build()
    }
    val config = parametersOperator.constructBackfillConfig(
      request.parameters, request.dry_run
    )

    return backfill.inTransactionReturning(
      "${name()}#JooqBackfillOperator#getNextBatchRange",
      request.partition_name
    ) { dslContext ->
      GetNextBatchRangeResponse.Builder()
        .batches(
          Streams.stream(BatchRangeIterator(backfill, dslContext, request, config))
            .limit(request.compute_count_limit)
            .collect(Collectors.toList())
        )
        .build()
    }
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    val config = parametersOperator.constructBackfillConfig(
      request.parameters, request.dry_run
    )

    val keysInRange = backfill.inTransactionReturning(
      "${name()}#JooqBackfillOperator#runBatch", request.partition_name
    ) { dslContext ->
      dslContext.select(backfill.compoundKeyFields)
        .from(backfill.table)
        .where(backfill.filterCondition(config))
        .and(
          backfill.compareCompoundKey(
            backfill.fromByteString(request.batch_range.start), CompoundKeyComparer<K>::gte
          )
        )
        .and(
          backfill.compareCompoundKey(
            backfill.fromByteString(request.batch_range.end), CompoundKeyComparer<K>::lte
          )
        )
        .orderBy(backfill.compoundKeyFields)
        .fetch { backfill.recordToKey(it) }
    }

    backfill.backfill(
      BackfillBatch(
        shardName = request.partition_name,
        transacter = backfill.getTransacter(request.partition_name),
        keys = keysInRange,
        config = config
      )
    )

    return RunBatchResponse.Builder().build()
  }

  private fun computeOverallRange(
    partitionName: String,
    request: PrepareBackfillRequest
  ): KeyRange {
    return backfill.getUnfilteredBoundaryKeyValue(partitionName) { it.asc() }
      ?.let { computedRangeStart ->
        computeOverallRange(
          partitionName,
          request,
          computedRangeStart
        )
      }
      ?: KeyRange.Builder().build()
  }

  private fun computeOverallRange(
    partitionName: String,
    request: PrepareBackfillRequest,
    computedRangeStart: K
  ): KeyRange {
    val rangeStart: ByteString =
      if (request.range == null || request.range.start == null) backfill.toByteString(
        computedRangeStart
      ) else request.range.start
    val rangeEnd: ByteString =
      if (request.range == null || request.range.end == null) backfill.toByteString(
        backfill.getUnfilteredBoundaryKeyValue(partitionName) { it.desc() }
          ?: throw IllegalStateException("We should always get the last key value")
      ) else request.range.end
    return KeyRange.Builder()
      .start(rangeStart)
      .end(rangeEnd)
      .build()
  }
}
