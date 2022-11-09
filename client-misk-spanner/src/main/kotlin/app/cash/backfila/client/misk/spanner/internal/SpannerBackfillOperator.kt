package app.cash.backfila.client.misk.spanner.internal

import app.cash.backfila.client.misk.spanner.SpannerBackfill
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.cloud.spanner.Key
import com.google.cloud.spanner.KeyRange
import com.google.cloud.spanner.KeySet
import com.squareup.moshi.Moshi
import misk.moshi.adapter
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

class SpannerBackfillOperator<Param : Any> internal constructor(
  override val backfill: SpannerBackfill<Param>,
  private val parametersOperator: BackfilaParametersOperator<Param>,
  backend: SpannerBackend,
) : BackfillOperator {
  private var moshi: Moshi = backend.moshi
  private val adapter = moshi.adapter<List<String>>()

  override fun name() = backfill.javaClass.toString()

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    val config =
      parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)
    backfill.validate(config)

    val partitions = listOf(
      PrepareBackfillResponse.Partition.Builder()
        .backfill_range(request.range)
        .partition_name("partition")
        .build()
    )

    return PrepareBackfillResponse.Builder()
      .partitions(partitions)
      .build()
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    // Establish a range to scan - either we want to start at the first key,
    // or start from (and exclude) the last key that was scanned.
    val range = if (request.previous_end_key == null) {
      KeySet.all()
    } else {
      val previousEndKey = adapter.fromJson(request.previous_end_key.utf8())!!
      KeySet.range(
        KeyRange.openClosed(
          Key.of(*previousEndKey.toTypedArray()),
          Key.of(),
        )
      )
    }

    // Query the table with the desired range, only fetching the components of the primary key.
    val query = backfill.dbClient.singleUseReadOnlyTransaction()
      .read(backfill.tableName, range, backfill.primaryKeyColumns)

    val keys = mutableListOf<ByteString>()

    // For each result, until we reach the maximum scan size, create a key representation that
    // can be used to uniquely identify a result row.
    var numberToScan = request.scan_size
    while (numberToScan > 0 && query.next()) {
      val newKey = adapter.toJson(
        backfill.primaryKeyColumns.map { query.getString(it) }
      ).encodeUtf8()
      keys.add(newKey)
      numberToScan -= 1
    }
    query.close()

    // Return the starting and ending keys obtained from the scan.
    val batches = keys.chunked(request.batch_size.toInt()).map {
      GetNextBatchRangeResponse.Batch.Builder()
        .batch_range(
          app.cash.backfila.protos.clientservice.KeyRange.Builder()
            .start(it.first())
            .end(it.last())
            .build()
        )
        .scanned_record_count(it.size.toLong())
        .matching_record_count(it.size.toLong())
        .build()
    }

    return GetNextBatchRangeResponse.Builder()
      .batches(batches)
      .build()
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    val config =
      parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)

    // Create a range that encompasses the batch's starting and ending keys.
    val startKey = adapter.fromJson(request.batch_range.start.utf8())!!.toTypedArray()
    val endKey = adapter.fromJson(request.batch_range.end.utf8())!!.toTypedArray()
    val keyRange = KeyRange.closedClosed(
      Key.of(*startKey),
      Key.of(*endKey),
    )

    // Let the backfill do whatever it wants for the given batch.
    backfill.runBatch(keyRange, config)

    return RunBatchResponse.Builder()
      .build()
  }
}
