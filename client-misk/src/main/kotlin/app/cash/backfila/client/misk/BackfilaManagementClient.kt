package app.cash.backfila.client.misk

import okio.ByteString

interface BackfilaManagementClient {
  fun <B : Backfill<*, *, P>, P : Any> createAndStart(
    backfillClass: Class<B>,
    dry_run: Boolean,
    num_threads: Int? = null,
    parameters: P? = null,
    extra_sleep_ms: Long? = null,
    backoff_schedule: String? = null,
    batch_size: Long? = null,
    scan_size: Long? = null,
    pkey_range_start: ByteString? = null,
    pkey_range_end: ByteString? = null
  )
}
