package app.cash.backfila.client.internal

import app.cash.backfila.client.UnknownBackfillException
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillOperator
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import wisp.logging.getLogger
import kotlin.jvm.Throws

/**
 * Creates BackfillOperators using the registered Backends.
 */
@Singleton
class BackfillOperatorFactory @Inject constructor(
  private val backends: Set<BackfillBackend>
) {
  /**
   * Caches backfill instances by backfill id, which comes from backfila. We might have multiple
   * instances of the same backfill class, but the config is immutable per id so it is safe to
   * cache even if the backfill stores some state.
   */
  private val instanceCache: Cache<String, BackfillOperator> = CacheBuilder.newBuilder()
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .build()

  @Throws(UnknownBackfillException::class)
  fun create(backfillName: String, backfillId: String): BackfillOperator {
    return instanceCache.get(backfillId) {
      for (backend in backends) {
        val backfillOperator = backend.create(backfillName, backfillId)
        if (backfillOperator != null) {
          return@get backfillOperator
        }
      }
      logger.warn("Unknown backfill $backfillName, was it deleted while running?")
      throw UnknownBackfillException("Unknown backfill $backfillName")
    }
  }

  companion object {
    private val logger = getLogger<BackfillOperatorFactory>()
  }
}
