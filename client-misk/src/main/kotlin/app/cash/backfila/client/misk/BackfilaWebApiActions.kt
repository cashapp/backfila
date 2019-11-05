package app.cash.backfila.client.misk

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.inject.Injector
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import misk.exceptions.BadRequestException
import misk.logging.getLogger
import misk.security.authz.Authenticated
import misk.web.Post
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

@Singleton
internal class BackfilaClassResolver @Inject constructor(
  private val injector: Injector,
  @ForBackfila private val backfills: Map<String, Class<out Backfill>>
) {
  /**
   * Caches backfill instances by backfill id, which comes from backfila. We might have multiple
   * instances of the same backfill class, but the config is immutable per id so it is safe to
   * cache even if the backfill stores some state.
   */
  private val instanceCache: Cache<String, Backfill> = CacheBuilder.newBuilder()
      .expireAfterAccess(10, TimeUnit.MINUTES)
      .build()

  internal fun getBackfill(name: String, backfillId: String): Backfill {
    try {
      return instanceCache.get(backfillId) {
        val backfillClass = backfills[name]
        if (backfillClass == null) {
          logger.warn("Unknown backfill %s, was it deleted while running?", name)
          throw BadRequestException("Unknown backfill $name")
        }
        injector.getInstance(backfillClass)
      }
    } catch (e: ExecutionException) {
      throw RuntimeException(e)
    }
  }

  companion object {
    val logger = getLogger<BackfilaClassResolver>()
  }
}

internal class PrepareBackfillAction @Inject constructor(
  private val backfilaClassResolver: BackfilaClassResolver
) : WebAction {
  @Post("/backfila/prepare-backfill")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    logger.info { "Preparing backfill `${request.backfill_name}::${request.backfill_id}`" }

    val backfill = backfilaClassResolver.getBackfill(request.backfill_name, request.backfill_id)
    return backfill.prepareBackfill(request)
  }

  companion object {
    val logger = getLogger<PrepareBackfillAction>()
  }
}

internal class GetNextBatchRangeAction @Inject constructor(
  private val backfilaClassResolver: BackfilaClassResolver
) : WebAction {
  @Post("/backfila/get-next-batch-range")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    logger.info {
      "Computing batch for backfill `${request.backfill_name}::${request.instance_name}" +
          "::${request.backfill_id}`. Previous end: `${request.previous_end_key}`"
    }

    val backfill = backfilaClassResolver.getBackfill(request.backfill_name, request.backfill_id)
    val nextBatchRange = backfill.getNextBatchRange(request)
    logger.info {
      "Next batches computed for backfill " +
          "`${request.backfill_name}::${request.instance_name}::${request.backfill_id}`. " +
          "${nextBatchRange.batches}"
    }
    return nextBatchRange
  }

  companion object {
    val logger = getLogger<GetNextBatchRangeAction>()
  }
}

internal class RunBatchAction @Inject constructor(
  private val backfilaClassResolver: BackfilaClassResolver
) : WebAction {
  @Post("/backfila/run-batch")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  fun runBatch(request: RunBatchRequest): RunBatchResponse {
    logger.info {
      "Running backfila batch " +
          "`${request.backfill_name}::${request.instance_name}::${request.backfill_id}`: " +
          "[${request.batch_range.start.utf8()}, ${request.batch_range.end.utf8()}]"
    }

    val backfill = backfilaClassResolver.getBackfill(request.backfill_name, request.backfill_id)
    return backfill.runBatch(request)
  }

  companion object {
    val logger = getLogger<RunBatchAction>()
  }
}
