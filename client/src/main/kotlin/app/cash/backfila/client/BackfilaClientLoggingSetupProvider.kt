package app.cash.backfila.client

import javax.inject.Inject

interface BackfilaClientLoggingSetupProvider {
  fun <T> withBackfillRunLogging(backfillName: String, backfillId: String, wrapped: () -> T): T
  fun <T> withBackfillPartitionLogging(backfillName: String, backfillId: String, partitionName: String, wrapped: () -> T): T
}

class BackfilaClientNoLoggingSetupProvider
@Inject constructor() : BackfilaClientLoggingSetupProvider {
  override fun <T> withBackfillRunLogging(backfillName: String, backfillId: String, wrapped: () -> T): T {
    return wrapped.invoke()
  }

  override fun <T> withBackfillPartitionLogging(backfillName: String, backfillId: String, partitionName: String, wrapped: () -> T): T {
    return wrapped.invoke()
  }
}
