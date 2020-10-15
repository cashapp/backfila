package app.cash.backfila.client.misk.internal

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import misk.exceptions.BadRequestException
import misk.logging.getLogger

interface BackfillOperator {
  val backfill: Any
  fun name(): String
  fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse
  fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse
  fun runBatch(request: RunBatchRequest): RunBatchResponse

  /** Service provider interface for backends like Hibernate and DynamoDb. */
  interface Backend {
    fun create(backfillName: String, backfillId: String): BackfillOperator?
  }

  @Singleton
  class Factory @Inject constructor(
    private val backends: Set<Backend>
  ) {
    /**
     * Caches backfill instances by backfill id, which comes from backfila. We might have multiple
     * instances of the same backfill class, but the config is immutable per id so it is safe to
     * cache even if the backfill stores some state.
     */
    private val instanceCache: Cache<String, BackfillOperator> = CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build()

    fun create(backfillName: String, backfillId: String): BackfillOperator {
      return instanceCache.get(backfillId) {
        for (backend in backends) {
          val backfillOperator = backend.create(backfillName, backfillId)
          if (backfillOperator != null) {
            return@get backfillOperator
          }
        }
        logger.warn("Unknown backfill $backfillName, was it deleted while running?")
        throw BadRequestException("Unknown backfill $backfillName")
      }
    }

    companion object {
      private val logger = getLogger<Factory>()
    }
  }
}
