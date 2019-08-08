package com.squareup.backfila.service

import com.google.common.base.Preconditions.checkState
import com.squareup.backfila.client.BackfilaClientServiceClient
import com.squareup.backfila.client.BackfilaClientServiceClientProvider
import com.squareup.protos.backfila.clientservice.GetNextBatchRangeResponse
import com.squareup.protos.backfila.clientservice.RunBatchRequest
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.logging.getLogger
import okio.ByteString
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlin.math.max

val DEFAULT_BACKOFF_SCHEDULE = listOf(5_000L, 15_000L, 30_000L)

/**
 * Coordinator of the backfill run. Starts a few actors as coroutines and updates the lease.
 */
class BackfillRunner private constructor(
  val factory: Factory,
  val backfillName: String,
  val instanceName: String,
  val backfillRunId: Id<DbBackfillRun>,
  val instanceId: Id<DbRunInstance>,
  val leaseToken: String,
  val backoffSchedule: List<Long>
) {
  /** Metadata about the backfill from the database. Refreshed regularly. */
  lateinit var metadata: BackfillMetaData
    private set

  /**
   * Set to false when subtasks should begin to gracefully stop.
   * We cancel the coroutines to forcefully stop them.
   */
  private var running = true

  private var failuresSinceSuccess = 0
  private var backoffUntil: Instant? = null

  val client by lazy { createClient() }

  fun stop() {
    // Once false, the leasing task will exit and the child coroutines will be cancelled.
    running = false
  }

  fun run() {
    logger.info { "Runner starting: ${logLabel()} " }

    metadata = factory.transacter.transaction { session -> loadMetaData(session) }

    val batchPrecomputer = BatchPrecomputer(this)
    val batchQueuer = BatchQueuer(this, metadata.numThreads)
    val batchRunner = BatchRunner(this, batchQueuer.nextBatchChannel(),
        metadata.numThreads)
    val batchAwaiter = BatchAwaiter(this, batchRunner.runChannel(),
        batchRunner.rpcBackpressureChannel())

    // All our tasks run on this thread.
    runBlocking {
      batchPrecomputer.run(this)
      batchQueuer.run(this)
      batchRunner.run(this)
      batchAwaiter.run(this)

      checkAndUpdateLeaseUntilPausedOrComplete()
      coroutineContext.cancelChildren()
    }

    logger.info { "Runner cleaning up: ${logLabel()}" }
    clearLease()
    logger.info { "Runner finished: ${logLabel()}" }
  }

  private suspend fun checkAndUpdateLeaseUntilPausedOrComplete() {
    while (running) {
      val dbRunning = factory.transacter.transaction { session ->
        val dbRunInstance = session.load(instanceId)

        if (dbRunInstance.run_state != BackfillState.RUNNING) {
          logger.info { "Backfill is no longer in RUNNING state, stopping runner ${logLabel()}" }
          running = false
          return@transaction false
        }
        if (dbRunInstance.lease_token != leaseToken) {
          throw IllegalStateException("Backfill instance $instanceId has been stolen! " +
              "our token: $leaseToken, new token: ${dbRunInstance.lease_token}")
        }
        // Extend our lease regularly.
        dbRunInstance.lease_expires_at = factory.clock.instant() + LeaseHunter.LEASE_DURATION

        // While we're here, refresh metadata about the backfill in case a user made some changes,
        // such as changing batch_size or num_threads. This way we keep metadata updated but don't
        // have to load it repeatedly in every task.
        metadata = loadMetaData(session)

        return@transaction true
      }
      if (!dbRunning) break
      delay(1000)
    }
  }

  private fun loadMetaData(session: Session): BackfillMetaData {
    val dbRunInstance = session.load(instanceId)
    return BackfillMetaData(
        dbRunInstance.backfill_run_id,
        dbRunInstance.pkey_cursor,
        dbRunInstance.pkey_range_start,
        dbRunInstance.pkey_range_end,
        dbRunInstance.backfill_run.parameters(),
        dbRunInstance.backfill_run.batch_size,
        dbRunInstance.backfill_run.scan_size,
        dbRunInstance.backfill_run.dry_run,
        dbRunInstance.backfill_run.num_threads,
        dbRunInstance.precomputing_done,
        dbRunInstance.precomputing_pkey_cursor
    )
  }

  private fun createClient(): BackfilaClientServiceClient {
    val (serviceName, connector) = factory.transacter.transaction { session ->
      val dbRunInstance = session.load(instanceId)
      val service = dbRunInstance.backfill_run.registered_backfill.service
      Pair(service.registry_name, service.connector)
    }
    return factory.clientProvider.clientFor(serviceName, connector)
  }

  fun runBatchRequest(
    batch: GetNextBatchRangeResponse.Batch
  ) = RunBatchRequest(
      metadata.backfillRunId.toString(),
      backfillName,
      instanceName,
      batch.batch_range,
      metadata.parameters,
      metadata.dryRun,
      null
  )

  fun onRpcSuccess() {
    failuresSinceSuccess = 0
  }

  fun onRpcFailure() {
    // If there is an intermittent server issue, all the current batches will likely fail.
    // So to consider those as only one failure, only increment failure count if the backoff
    // finished.
    if (backingOff()) {
      logger.info { "Ignoring rpc error because runner is already backing off ${logLabel()}" }
      return
    }
    failuresSinceSuccess++
    if (failuresSinceSuccess > backoffSchedule.size) {
      logger.info {
        "Pausing backfill ${logLabel()} due to too many consecutive failures: $failuresSinceSuccess"
      }
      pauseBackfill()
    } else {
      backoffUntil = factory.clock.instant().plusMillis(
          backoffSchedule[failuresSinceSuccess - 1])
    }
  }

  fun backingOff() = backoffUntil?.isAfter(factory.clock.instant()) ?: false
  fun backoffMs() = max(0, Duration.between(factory.clock.instant(), backoffUntil!!).toMillis())

  private fun pauseBackfill() {
    factory.transacter.transaction { session ->
      val dbRunInstance = session.load(instanceId)
      checkState(dbRunInstance.backfill_run.state == BackfillState.RUNNING)
      dbRunInstance.backfill_run.setState(session, factory.queryFactory,
          BackfillState.PAUSED)
    }
  }

  fun clearLease() {
    factory.transacter.transaction { session ->
      val dbRunInstance = session.load(instanceId)
      if (dbRunInstance.lease_token != leaseToken) {
        logger.warn { "Lost lease on instance $instanceId, can't release it" }
        return@transaction
      }
      dbRunInstance.clearLease()
      logger.info { "Released lease on ${logLabel()}" }
    }
  }

  fun logLabel() = "$backfillName::$instanceName::$instanceId"

  data class BackfillMetaData(
    val backfillRunId: Id<DbBackfillRun>,
    val pkeyCursor: ByteString?,
    val pkeyStart: ByteString?,
    val pkeyEnd: ByteString?,
    val parameters: Map<String, ByteString>?,
    val batchSize: Long,
    val scanSize: Long,
    val dryRun: Boolean,
    val numThreads: Int,
    val precomputingDone: Boolean,
    val precomputingPkeyCursor: ByteString?
  )

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
          dbRunInstance.backfill_run_id,
          dbRunInstance.id,
          leaseToken,
          dbRunInstance.backfill_run.backoffSchedule() ?: DEFAULT_BACKOFF_SCHEDULE
      )
    }
  }
}