package app.cash.backfila.client

import okio.ByteString

interface BackfilaManagementClient {
  fun <B : Backfill> createAndStart(
    backfillClass: Class<B>,
    dry_run: Boolean,
    num_threads: Int? = null,
    parameters: Any? = null,
    extra_sleep_ms: Long? = null,
    backoff_schedule: String? = null,
    batch_size: Long? = null,
    scan_size: Long? = null,
    pkey_range_start: ByteString? = null,
    pkey_range_end: ByteString? = null
  )
}
