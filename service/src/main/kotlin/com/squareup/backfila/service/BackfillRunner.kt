package com.squareup.backfila.service

import com.google.common.annotations.VisibleForTesting
import com.squareup.backfila.client.BackfilaClientServiceClientProvider
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeRequest
import com.squareup.protos.backfila.clientservice.KeyRange
import com.squareup.protos.backfila.clientservice.RunBatchRequest
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.logging.getLogger
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import java.time.Clock
import javax.inject.Inject

class BackfillRunner private constructor(
  private val factory: Factory,
  val backfillName: String,
  val instanceName: String,
  val instanceId: Id<DbRunInstance>,
  val leaseToken: String
) {
  @Volatile private var running = true

  fun stop() {
    // TODO cancel futures (after 5s timeout? that needs another thread)
    // or we could just have bounded sleeps on futures and ignore pending ones on shutdown
    running = false
  }

  fun run() {
    logger.info { "Runner starting: $backfillName::$instanceName " }
    while (running) {
      logger.info { "Runner looping: $backfillName::$instanceName $leaseToken" }
      work()
    }
    logger.info { "Runner cleaning up: $backfillName::$instanceName" }
    cleanup()
    logger.info { "Runner finished: $backfillName::$instanceName" }
  }

  @VisibleForTesting
  fun work() {
    val (serviceName, connector) = factory.transacter.transaction { session ->
      val dbRunInstance = session.load(instanceId)
      val service = dbRunInstance.backfill_run.registered_backfill.service

      if (dbRunInstance.run_state != BackfillState.RUNNING) {
        running = false
        logger.info {
          "Backfill is no longer running, stopping runner for $backfillName::$instanceName"
        }
        return@transaction Pair(service.registry_name, service.connector)
      }
      if (dbRunInstance.lease_token != leaseToken) {
        throw IllegalStateException("Backfill instance $instanceId has been stolen! " +
            "our token: $leaseToken, new token: ${dbRunInstance.lease_token}")
      }
      // Extend our lease before doing long work
      dbRunInstance.lease_expires_at = factory.clock.instant() + LeaseHunter.LEASE_DURATION

      Pair(service.registry_name, service.connector)
    }

    if (!running) {
      return
    }

    val client = factory.clientProvider.clientFor(serviceName, connector)

    data class BackfillData(
      val backfillRunId: Id<DbBackfillRun>,
      val pkeyCursor: ByteString?,
      val pkeyStart: ByteString?,
      val pkeyEnd: ByteString?,
      val parameters: Map<String, ByteString>?,
      val batchSize: Long,
      val scanSize: Long,
      val dryRun: Boolean
    )

    val data = factory.transacter.transaction { session ->
      val dbRunInstance = session.load(instanceId)

      BackfillData(
          dbRunInstance.backfill_run_id,
          dbRunInstance.pkey_cursor,
          dbRunInstance.pkey_range_start,
          dbRunInstance.pkey_range_end,
          dbRunInstance.backfill_run.parameter_map?.mapValues { (k, v) -> v.decodeBase64()!! },
          dbRunInstance.backfill_run.batch_size,
          dbRunInstance.backfill_run.scan_size,
          dbRunInstance.backfill_run.dry_run
      )
    }

    val computeTimeLimitMs = 10_000L
    val computeCountLimit = 1L
    // TODO handle rpc error
    val nextBatchRange = client.getNextBatchRange(GetNextBatchRangeRequest(
        data.backfillRunId.toString(),
        backfillName,
        instanceName,
        data.batchSize,
        data.scanSize,
        data.pkeyCursor,
        KeyRange(data.pkeyStart, data.pkeyEnd),
        data.parameters,
        computeTimeLimitMs,
        computeCountLimit
    ))
    if (nextBatchRange.get().batches.isEmpty()) {
      logger.info { "No more batches, done: $backfillName::$instanceName" }
      factory.transacter.transaction { session ->
        val dbRunInstance = session.load(instanceId)
        dbRunInstance.run_state = BackfillState.COMPLETE

        maybeCompleteBackfill(session, dbRunInstance)
      }
      return
    }
    val batch = nextBatchRange.get().batches.first()
    if (batch.matching_record_count != 0L) {
      // TODO check for response error
      val runBatchResponse = client.runBatch(RunBatchRequest(
          data.backfillRunId.toString(),
          backfillName,
          instanceName,
          batch.batch_range,
          data.parameters,
          data.dryRun,
          null // TODO
      )).get()
    }
    factory.transacter.transaction { session ->
      val dbRunInstance = session.load(instanceId)
      dbRunInstance.pkey_cursor = batch.batch_range.end
      dbRunInstance.backfilled_scanned_record_count += batch.scanned_record_count
      dbRunInstance.backfilled_matching_record_count += batch.matching_record_count
    }
  }

  private fun maybeCompleteBackfill(
    session: Session,
    dbRunInstance: DbRunInstance
  ) {
    logger.info { "Instance $backfillName::$instanceName completed" }
    // If multiple instances finish they need to be synchronized to see eachother's states
    // If all states are COMPLETE the whole backfill will be completed.
    dbRunInstance.backfill_run.version++
    val instances = dbRunInstance.backfill_run.instances(session, factory.queryFactory)
    if (instances.all { it.run_state == BackfillState.COMPLETE }) {
      dbRunInstance.backfill_run.complete()
      logger.info { "Backfill $backfillName completed" }
      // TODO post-completion tasks...
    }
  }

  private fun cleanup() {
    factory.transacter.transaction { session ->
      val dbRunInstance = session.load(instanceId)
      if (dbRunInstance.lease_token != leaseToken) {
        logger.warn { "Lost lease on instance $instanceId, can't release it" }
        return@transaction
      }
      dbRunInstance.clearLease()
      logger.info { "Released lease on $backfillName::$instanceName" }
    }
  }

  companion object {
    private val logger = getLogger<BackfillRunner>()
  }

  class Factory @Inject internal constructor(
    @BackfilaDb val transacter: Transacter,
    val clock: Clock,
    val clientProvider: BackfilaClientServiceClientProvider,
    val queryFactory: Query.Factory
  ) {
    fun create(
      @Suppress("UNUSED_PARAMETER") session: Session,
      dbRunInstance: DbRunInstance,
      leaseToken: String
    ): BackfillRunner {
      return BackfillRunner(
          this,
          dbRunInstance.backfill_run.registered_backfill.name,
          dbRunInstance.instance_name,
          dbRunInstance.id,
          leaseToken
      )
    }
  }
}