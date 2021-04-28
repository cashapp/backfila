package app.cash.backfila.service.runner

import app.cash.backfila.service.persistence.DbBackfillRun
import javax.inject.Inject
import misk.hibernate.Id
import org.slf4j.MDC
import wisp.logging.getLogger

interface BackfillRunnerLoggingSetupProvider {
  fun <T> withLogging(backfillName: String, backfillId: Id<DbBackfillRun>, partitionName: String, wrapped: () -> T): T
}

class BackfillRunnerNoLoggingSetupProvider
@Inject constructor() : BackfillRunnerLoggingSetupProvider {
  override fun <T> withLogging(backfillName: String, backfillId: Id<DbBackfillRun>, partitionName: String, wrapped: () -> T): T {
    return wrapped.invoke()
  }
}

class BackfillRunnerMDCLoggingSetupProvider
@Inject constructor() : BackfillRunnerLoggingSetupProvider {

  override fun <T> withLogging(backfillName: String, backfillId: Id<DbBackfillRun>, partitionName: String, wrapped: () -> T): T {
    try {
      MDC.put(MDC_BACKFILL_NAME, backfillName)
      MDC.put(MDC_BACKFILL_ID, backfillId.toString())
      MDC.put(MDC_PARTITION_NAME, partitionName)
    } catch (e: Exception) {
      logger.debug("Exception setting log context context", e)
    }
    return try {
      wrapped.invoke()
    } finally {
      try {
        MDC.remove(MDC_BACKFILL_NAME)
        MDC.remove(MDC_BACKFILL_ID)
        MDC.remove(MDC_PARTITION_NAME)
      } catch (e: Exception) {
        logger.debug("Exception removing log context context", e)
      }
    }
  }

  companion object {
    val logger = getLogger<BackfillRunnerMDCLoggingSetupProvider>()
    const val MDC_BACKFILL_NAME = "backfill_name"
    const val MDC_BACKFILL_ID = "backfill_id"
    const val MDC_PARTITION_NAME = "partition_name"
  }
}
