package app.cash.backfila.service.runner

import app.cash.backfila.client.BackfilaClientServiceClient
import app.cash.backfila.client.ConnectorProvider
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PipelinedData
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import app.cash.backfila.service.BackfilaMetrics
import app.cash.backfila.service.SlackHelper
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import app.cash.backfila.service.persistence.DbRunPartition
import app.cash.backfila.service.runner.statemachine.BatchAwaiter
import app.cash.backfila.service.runner.statemachine.BatchPrecomputer
import app.cash.backfila.service.runner.statemachine.BatchQueuer
import app.cash.backfila.service.runner.statemachine.BatchRunner
import app.cash.backfila.service.runner.statemachine.RunBatchException
import app.cash.backfila.service.scheduler.LeaseHunter
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Stopwatch
import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.load
import okio.ByteString
import org.apache.commons.lang3.exception.ExceptionUtils
import wisp.logging.getLogger

val DEFAULT_BACKOFF_SCHEDULE = listOf(5_000L, 15_000L, 30_000L)

/** How frequently to extend the lease and update state. */
internal val EXTEND_LEASE_PERIOD: Duration = Duration.ofMillis(1000)

/**
 * Coordinator of the backfill run. Starts a few actors as coroutines and updates the lease.
 */
class BackfillRunner private constructor(
  val factory: Factory,
  serviceName: String,
  val backfillName: String,
  val partitionName: String,
  val backfillRunId: Id<DbBackfillRun>,
  val partitionId: Id<DbRunPartition>,
  private val leaseToken: String
) {
  /** Metadata about the backfill from the database. Refreshed regularly. */
  lateinit var metadata: BackfillMetaData
    private set

  val metricLabels: Array<String> = arrayOf(
    serviceName,
    backfillName,
    backfillRunId.toString(),
    partitionName
  )

  /**
   * Set to false when subtasks should begin to gracefully stop.
   * We cancel the coroutines to forcefully stop them.
   */
  private var running = true

  private var failuresSinceSuccess = 0

  /** Backoff for all RPCs for this runner. */
  val globalBackoff = Backoff(factory.clock)

  /** Backoff just for RunBatch RPCs. */
  val runBatchBackoff = Backoff(factory.clock)

  val client by lazy { createClient() }

  fun stop() {
    // Once false, the leasing task will exit and the child coroutines will be cancelled.
    running = false
  }

  private lateinit var batchPrecomputer: BatchPrecomputer
  private lateinit var batchAwaiter: BatchAwaiter

  fun run() {
    factory.loggingSetupProvider.withLogging(backfillName, backfillRunId, partitionName) {
      runBlocking(MDCContext()) {
        start(this)
      }
      logger.info { "Runner cleaning up lease: ${logLabel()}" }
      clearLease()
      logger.info { "Runner finished: ${logLabel()}" }
    }
  }

  @VisibleForTesting
  fun start(coroutineScope: CoroutineScope) {
    logger.info { "Runner starting: ${logLabel()} " }

    metadata = factory.transacter.transaction { session -> loadMetaData(session) }

    batchPrecomputer = BatchPrecomputer(this)
    val batchQueuer = BatchQueuer(this, metadata.numThreads)
    val batchRunner = BatchRunner(
      this,
      batchQueuer.nextBatchChannel(),
      metadata.numThreads
    )
    batchAwaiter = BatchAwaiter(
      this,
      batchRunner.runChannel(),
      batchRunner.rpcBackpressureChannel()
    )

    // All our tasks run on this thread.
    coroutineScope.launch {
      batchPrecomputer.run(this)
      batchQueuer.run(this)
      batchRunner.run(this)
      batchAwaiter.run(this)

      checkAndUpdateLeaseUntilPausedOrComplete()
      logger.info { "Runner cleaning up coroutines: ${logLabel()}" }
      coroutineContext.cancelChildren()
    }
  }

  private suspend fun checkAndUpdateLeaseUntilPausedOrComplete() {
    try {
      while (running) {
        delay(EXTEND_LEASE_PERIOD.toMillis())

        checkAndUpdateLeaseAndPersistState()
      }
    } catch (e: Throwable) {
      logger.info(e) { "Encountered an exception while monitoring the lease for ${logLabel()}" }
    }
  }

  private fun checkAndUpdateLeaseAndPersistState() {
    factory.transacter.transaction { session ->
      val dbRunPartition = session.load(partitionId)

      if (dbRunPartition.lease_token != leaseToken) {
        throw IllegalStateException(
          "Backfill partition $partitionId has been stolen! " +
            "our token: $leaseToken, new token: ${dbRunPartition.lease_token}"
        )
      }

      // We save state here instead of in the actors to avoid excessive writes.
      // This state is saved for when the runner loses the lease and another runner takes it over.
      // Therefore it is sufficient to save it every so often rather than after every operation.
      batchPrecomputer.updateProgress(dbRunPartition)
      batchAwaiter.updateProgress(dbRunPartition)

      // Now that state is stored, check if we should exit.
      if (dbRunPartition.run_state != BackfillState.RUNNING) {
        logger.info { "Backfill is no longer in RUNNING state, stopping runner ${logLabel()}" }
        running = false
        return@transaction
      }

      // Extend our lease regularly.
      dbRunPartition.lease_expires_at = factory.clock.instant() + LeaseHunter.LEASE_DURATION

      // While we're here, refresh metadata about the backfill in case a user made some changes,
      // such as changing batch_size or num_threads. This way we keep metadata updated but don't
      // have to load it repeatedly in every task.
      metadata = loadMetaData(session)
    }
  }

  private fun loadMetaData(session: Session): BackfillMetaData {
    val dbRunPartition = session.load(partitionId)
    return BackfillMetaData(
      backfillRunId = dbRunPartition.backfill_run_id,
      pkeyCursor = dbRunPartition.pkey_cursor,
      pkeyStart = dbRunPartition.pkey_range_start,
      pkeyEnd = dbRunPartition.pkey_range_end,
      parameters = dbRunPartition.backfill_run.parameters(),
      batchSize = dbRunPartition.backfill_run.batch_size,
      scanSize = dbRunPartition.backfill_run.scan_size,
      dryRun = dbRunPartition.backfill_run.dry_run,
      numThreads = dbRunPartition.backfill_run.num_threads,
      precomputingDone = dbRunPartition.precomputing_done,
      precomputingPkeyCursor = dbRunPartition.precomputing_pkey_cursor,
      computedScannedRecordCount = dbRunPartition.computed_scanned_record_count,
      computedMatchingRecordCount = dbRunPartition.computed_matching_record_count,
      extraSleepMs = dbRunPartition.backfill_run.extra_sleep_ms,
      backoffSchedule = dbRunPartition.backfill_run.backoffSchedule() ?: DEFAULT_BACKOFF_SCHEDULE,
      backfilledScannedRecordCount = dbRunPartition.backfilled_scanned_record_count,
      backfilledMatchingRecordCount = dbRunPartition.backfilled_matching_record_count,
    )
  }

  private fun createClient(): BackfilaClientServiceClient {
    data class DbData(
      val serviceName: String,
      val connector: String,
      val connectorExtraData: String?
    )

    val dbData = factory.transacter.transaction { session ->
      val dbRunPartition = session.load(partitionId)
      val service = dbRunPartition.backfill_run.registered_backfill.service
      DbData(service.registry_name, service.connector, service.connector_extra_data)
    }
    return factory.connectorProvider.clientProvider(dbData.connector)
      .clientFor(dbData.serviceName, dbData.connectorExtraData)
  }

  fun runBatchAsync(
    scope: CoroutineScope,
    batch: GetNextBatchRangeResponse.Batch,
    pipelinedData: PipelinedData? = null
  ): Deferred<RunBatchResponse> {
    // Supervisor here allows us to handle the exception, rather than failing the job.
    return scope.async(SupervisorJob() + CoroutineName("RunBatch RPC")) {
      val stopwatch = Stopwatch.createStarted()

      val response = client.runBatch(
        RunBatchRequest(
          metadata.backfillRunId.toString(),
          backfillName,
          partitionName,
          batch.batch_range,
          metadata.parameters,
          metadata.dryRun,
          pipelinedData,
          metadata.batchSize
        )
      )

      factory.metrics.runBatchDuration.record(
        stopwatch.elapsed().toMillis().toDouble(),
        *metricLabels
      )
      response
    }
  }

  fun onRpcSuccess() {
    failuresSinceSuccess = 0
  }

  suspend fun onRpcFailure(
    exception: Exception,
    action: String,
    elapsed: Duration
  ) {
    // If there is an intermittent server issue, all the current batches will likely fail.
    // So to consider those as only one failure, only increment failure count if the backoff
    // finished.
    if (globalBackoff.backingOff()) {
      logger.info { "Ignoring rpc error because runner is already backing off ${logLabel()}" }
      recordErrorEvent(exception, action, elapsed, backoffMs = null, paused = false)
      return
    }

    failuresSinceSuccess++
    if (failuresSinceSuccess > metadata.backoffSchedule.size) {
      logger.info {
        "Pausing backfill ${logLabel()} due to too many consecutive failures: $failuresSinceSuccess"
      }
      if (pauseBackfill()) {
        factory.slackHelper.runErrored(backfillRunId)

        recordErrorEvent(exception, action, elapsed, backoffMs = null, paused = true)
      }

      // With very frequent errors the caller may never suspend and give other coroutines
      // a chance to execute. Suspend here to give the lease updater loop a chance to run
      // and exit the runner.
      running = false
      delay(1000)
    } else {
      val backoffMs = metadata.backoffSchedule[failuresSinceSuccess - 1]
      globalBackoff.addMillis(backoffMs)
      recordErrorEvent(exception, action, elapsed, backoffMs, paused = false)
    }
  }

  /** Returns true if the backfill was changed to paused, false if it was already paused. */
  private fun pauseBackfill(): Boolean {
    return factory.transacter.transaction { session ->
      val dbRunPartition = session.load(partitionId)
      if (dbRunPartition.backfill_run.state == BackfillState.RUNNING) {
        dbRunPartition.backfill_run.setState(
          session, factory.queryFactory,
          BackfillState.PAUSED
        )
        return@transaction true
      }
      return@transaction false
    }
  }

  fun clearLease() {
    factory.transacter.transaction { session ->
      val dbRunPartition = session.load(partitionId)
      if (dbRunPartition.lease_token != leaseToken) {
        logger.warn { "Lost lease on partition $partitionId, can't release it" }
        return@transaction
      }
      dbRunPartition.clearLease()
      logger.info { "Released lease on ${logLabel()}" }
    }
  }

  private fun recordErrorEvent(
    exception: Exception,
    action: String,
    elapsed: Duration,
    backoffMs: Long?,
    paused: Boolean
  ) {
    val elapsedMs = elapsed.toMillis()

    val endMessage = when {
      paused -> "paused backfill due to $failuresSinceSuccess consecutive errors"
      backoffMs != null -> "backing off for ${backoffMs}ms"
      else -> "already backing off"
    }

    factory.transacter.transaction { session ->
      when (exception) {
        is RunBatchException -> {
          session.save(
            DbEventLog(
              backfillRunId,
              partition_id = partitionId,
              type = DbEventLog.Type.ERROR,
              message = "error $action, client exception after ${elapsedMs}ms. $endMessage",
              extra_data = exception.stackTrace
            )
          )
        }
        is SocketTimeoutException -> {
          session.save(
            DbEventLog(
              backfillRunId,
              partition_id = partitionId,
              type = DbEventLog.Type.ERROR,
              message = "error $action, timeout after ${elapsedMs}ms. $endMessage",
              extra_data = ExceptionUtils.getStackTrace(exception)
            )
          )
        }
        else -> {
          session.save(
            DbEventLog(
              backfillRunId,
              partition_id = partitionId,
              type = DbEventLog.Type.ERROR,
              message = "error $action, RPC error after ${elapsedMs}ms. $endMessage",
              extra_data = ExceptionUtils.getStackTrace(exception)
            )
          )
        }
      }
    }
  }

  fun logLabel() = "$backfillName::$backfillRunId::$partitionName::$partitionId"

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
    val precomputingPkeyCursor: ByteString?,
    val computedScannedRecordCount: Long,
    val computedMatchingRecordCount: Long,
    val extraSleepMs: Long,
    val backoffSchedule: List<Long>,
    val backfilledScannedRecordCount: Long,
    val backfilledMatchingRecordCount: Long,
  )

  companion object {
    private val logger = getLogger<BackfillRunner>()
  }

  class Factory @Inject internal constructor(
    @BackfilaDb val transacter: Transacter,
    val clock: Clock,
    val queryFactory: Query.Factory,
    val connectorProvider: ConnectorProvider,
    val slackHelper: SlackHelper,
    val loggingSetupProvider: BackfillRunnerLoggingSetupProvider,
    val metrics: BackfilaMetrics,
  ) {
    fun create(
      @Suppress("UNUSED_PARAMETER") session: Session,
      dbRunPartition: DbRunPartition,
      leaseToken: String
    ): BackfillRunner {
      return BackfillRunner(
        this,
        dbRunPartition.backfill_run.service.registry_name,
        dbRunPartition.backfill_run.registered_backfill.name,
        dbRunPartition.partition_name,
        dbRunPartition.backfill_run_id,
        dbRunPartition.id,
        leaseToken
      )
    }
  }
}
