package app.cash.backfila.client

import app.cash.backfila.protos.managementclient.CheckStatusResponse
import app.cash.backfila.protos.managementclient.CreateAndStartResponse
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
    pkey_range_end: ByteString? = null,
  ): CreateAndStartResponse?

  fun checkStatus(
    backfill_run_id: Long,
  ): CheckStatusResponse?
}
