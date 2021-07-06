package app.cash.backfila.client

import app.cash.backfila.client.internal.BackfilaClient
import app.cash.backfila.client.spi.parametersToBytes
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import javax.inject.Inject
import okio.ByteString

class RealBackfilaManagementClient @Inject internal constructor(
  private val client: BackfilaClient
) : BackfilaManagementClient {
  override fun <B : Backfill> createAndStart(
    backfillClass: Class<B>,
    dry_run: Boolean,
    num_threads: Int?,
    parameters: Any?,
    extra_sleep_ms: Long?,
    backoff_schedule: String?,
    batch_size: Long?,
    scan_size: Long?,
    pkey_range_start: ByteString?,
    pkey_range_end: ByteString?
  ) {
    client.createAndStartBackfill(
      CreateAndStartBackfillRequest.Builder()
        .create_request(
          CreateBackfillRequest.Builder()
            .backfill_name(backfillClass.name)
            .dry_run(dry_run)
            .apply {
              if (parameters != null) {
                parameter_map(parametersToBytes(parameters))
              }
            }
            .num_threads(num_threads)
            .extra_sleep_ms(extra_sleep_ms)
            .backoff_schedule(backoff_schedule)
            .batch_size(batch_size)
            .scan_size(scan_size)
            .pkey_range_start(pkey_range_start)
            .pkey_range_end(pkey_range_end)
            .build()
        ).build()
    )
  }
}
