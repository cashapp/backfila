package app.cash.backfila.client

import app.cash.backfila.client.FakeBackfilaClientServiceClient.Companion.ResponseBehaviour.FAILURE
import app.cash.backfila.client.FakeBackfilaClientServiceClient.Companion.ResponseBehaviour.SUCCESS
import app.cash.backfila.client.FakeBackfilaClientServiceClient.Companion.ResponseBehaviour.TO_CHANNEL
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import misk.exceptions.BadRequestException
import okio.ByteString.Companion.encodeUtf8

@Singleton
class FakeBackfilaClientServiceClient @Inject constructor() : BackfilaClientServiceClient {
  val prepareBackfillResponses = LinkedList<PrepareBackfillResponse>()

  val getNextBatchRangeRequests = Channel<GetNextBatchRangeRequest>()
  /** Send empty data here to signal GetNextBatchRange should return the next batch. */
  val getNextBatchRangeResponses = Channel<Result<GetNextBatchRangeResponse>>()

  val runBatchRequests = Channel<RunBatchRequest>()
  /** Send responses or exceptions here to return them to the runner. */
  val runBatchResponses = Channel<Result<RunBatchResponse>>()

  // Storing our response behavior for our two partitions and the default. This allows one partition
  // to make progress ahead of the other.
  val eightyDashBehaviour = PartitionBehaviour(TO_CHANNEL, TO_CHANNEL)
  val dashEightyBehaviour = PartitionBehaviour(TO_CHANNEL, TO_CHANNEL)
  val defaultBehaviour = PartitionBehaviour(TO_CHANNEL, TO_CHANNEL)

  private fun getPartitionBehaviour(partitionName: String): PartitionBehaviour {
    return when (partitionName) {
      DASH_EIGHTY -> dashEightyBehaviour
      EIGHTY_DASH -> eightyDashBehaviour
      else -> defaultBehaviour
    }
  }

  fun dontBlockGetNextBatch() {
    eightyDashBehaviour.nextBatch = SUCCESS
    dashEightyBehaviour.nextBatch = SUCCESS
    defaultBehaviour.nextBatch = SUCCESS
  }

  fun dontBlockRunBatch() {
    eightyDashBehaviour.runBatch = SUCCESS
    dashEightyBehaviour.runBatch = SUCCESS
    defaultBehaviour.runBatch = SUCCESS
  }

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    if (prepareBackfillResponses.isNotEmpty()) {
      return prepareBackfillResponses.removeFirst()
    }
    return PrepareBackfillResponse.Builder()
      .partitions(
        listOf(
          PrepareBackfillResponse.Partition(
            DASH_EIGHTY,
            KeyRange("0".encodeUtf8(), "1000".encodeUtf8()),
            1_000_000L
          ),
          PrepareBackfillResponse.Partition(
            EIGHTY_DASH,
            KeyRange("0".encodeUtf8(), "1000".encodeUtf8()),
            null
          )
        )
      )
      .build()
  }

  override suspend fun getNextBatchRange(request: GetNextBatchRangeRequest):
    GetNextBatchRangeResponse {
      when (getPartitionBehaviour(request.partition_name).nextBatch) {
        TO_CHANNEL -> {
          getNextBatchRangeRequests.send(request)
          return getNextBatchRangeResponses.receive().getOrThrow()
        }
        FAILURE -> {
          throw BadRequestException("Forced Test GetNextBatchRange Failure")
        }
        SUCCESS -> {
          val nextStart = if (request.previous_end_key != null) {
            request.previous_end_key.utf8().toLong() + 1
          } else {
            request.backfill_range.start.utf8().toLong()
          }
          var nextEnd = nextStart + request.batch_size - 1
          if (nextEnd > request.backfill_range.end.utf8().toLong()) {
            nextEnd = request.backfill_range.end.utf8().toLong()
          }
          if (nextStart > request.backfill_range.end.utf8().toLong()) {
            return GetNextBatchRangeResponse(listOf())
          }
          return GetNextBatchRangeResponse(
            listOf(
              GetNextBatchRangeResponse.Batch(
                KeyRange(nextStart.toString().encodeUtf8(), nextEnd.toString().encodeUtf8()),
                nextEnd - nextStart + 1,
                nextEnd - nextStart + 1
              )
            )
          )
        }
      }
    }

  override suspend fun runBatch(request: RunBatchRequest): RunBatchResponse {
    when (getPartitionBehaviour(request.partition_name).runBatch) {
      TO_CHANNEL -> {
        runBatchRequests.send(request)
        return runBatchResponses.receive().getOrThrow()
      }
      FAILURE -> {
        throw BadRequestException("Forced Test RunBatch Failure")
      }
      SUCCESS -> {
        return RunBatchResponse.Builder().build()
      }
    }
  }

  companion object {
    // Partition constants
    val DASH_EIGHTY = "-80"
    val EIGHTY_DASH = "80-"

    data class PartitionBehaviour(
      var nextBatch: ResponseBehaviour,
      var runBatch: ResponseBehaviour
    )

    enum class ResponseBehaviour {
      TO_CHANNEL,
      SUCCESS,
      FAILURE
    }
  }
}
