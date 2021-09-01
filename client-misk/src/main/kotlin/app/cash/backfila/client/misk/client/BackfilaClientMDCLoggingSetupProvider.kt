package app.cash.backfila.client.misk.client

import app.cash.backfila.client.BackfilaClientLoggingSetupProvider
import javax.inject.Inject
import org.slf4j.MDC
import wisp.logging.getLogger

class BackfilaClientMDCLoggingSetupProvider @Inject constructor() :
  BackfilaClientLoggingSetupProvider {

  override fun <T> withBackfillRunLogging(backfillName: String, backfillId: String, wrapped: () -> T): T {
    try {
      MDC.put(MDC_BACKFILL_NAME, backfillName)
      MDC.put(MDC_BACKFILL_ID, backfillId)
    } catch (e: Exception) {
      logger.debug("Exception setting log context context", e)
    }
    return try {
      wrapped.invoke()
    } finally {
      cleanupLogging()
    }
  }

  override fun <T> withBackfillPartitionLogging(backfillName: String, backfillId: String, partitionName: String, wrapped: () -> T): T {
    try {
      MDC.put(MDC_BACKFILL_NAME, backfillName)
      MDC.put(MDC_BACKFILL_ID, backfillId)
      MDC.put(MDC_PARTITION_NAME, partitionName)
    } catch (e: Exception) {
      logger.debug("Exception setting log context context", e)
    }
    return try {
      wrapped.invoke()
    } finally {
      cleanupLogging()
    }
  }

  internal fun cleanupLogging() {
    try {
      MDC.remove(MDC_BACKFILL_NAME)
      MDC.remove(MDC_BACKFILL_ID)
      MDC.remove(MDC_PARTITION_NAME)
    } catch (e: Exception) {
      logger.debug("Exception removing log context context", e)
    }
  }

  companion object {
    val logger = getLogger<BackfilaClientMDCLoggingSetupProvider>()
    const val MDC_BACKFILL_NAME = "backfill_name"
    const val MDC_BACKFILL_ID = "backfill_id"
    const val MDC_PARTITION_NAME = "partition_name"
  }
}
