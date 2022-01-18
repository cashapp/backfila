package app.cash.backfila.service

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors.ENVOY
import app.cash.backfila.client.FakeBackfilaClientServiceClient
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.dashboard.StopBackfillRequest
import app.cash.backfila.dashboard.UpdateBackfillAction
import app.cash.backfila.dashboard.UpdateBackfillRequest
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.RunBatchResponse
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.protos.service.Parameter
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.runner.BackfillRunner
import app.cash.backfila.service.runner.EXTEND_LEASE_PERIOD
import app.cash.backfila.service.scheduler.LeaseHunter
import com.google.inject.Module
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class BackfillRunnerTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction
  @Inject lateinit var createBackfillAction: CreateBackfillAction
  @Inject lateinit var getBackfillRunsAction: GetBackfillRunsAction
  @Inject lateinit var getBackfillStatusAction: GetBackfillStatusAction
  @Inject lateinit var startBackfillAction: StartBackfillAction
  @Inject lateinit var stopBackfillAction: StopBackfillAction
  @Inject lateinit var scope: ActionScope
  @Inject lateinit var fakeClock: FakeClock
  @Inject lateinit var leaseHunter: LeaseHunter
  @Inject @BackfilaDb lateinit var transacter: Transacter
  @Inject lateinit var fakeBackfilaClientServiceClient: FakeBackfilaClientServiceClient
  @Inject lateinit var updateBackfillAction: UpdateBackfillAction

  @Test fun happyCompleteRun() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    fakeBackfilaClientServiceClient.dontBlockRunBatch()

    var runner = startBackfill()

    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isNull()
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }

    runBlockingTestCancellable {
      runner.start(this)
    }

    var status = getBackfillStatusAction.status(runner.backfillRunId.id)
    var partition = status.partitions.find { it.id == runner.partitionId.id }!!
    assertThat(partition.pkey_cursor).isEqualTo("1000")
    assertThat(partition.state).isEqualTo(BackfillState.COMPLETE)
    // Not all partitions complete.
    assertThat(status.state).isEqualTo(BackfillState.RUNNING)

    with(getBackfillRunsAction.backfillRuns("deep-fryer")) {
      assertThat(running_backfills).hasSize(1)
      assertThat(paused_backfills).hasSize(0)
      val run = running_backfills.single()
      assertThat(run.backfilled_matching_record_count).isEqualTo(1001)
      assertThat(run.computed_matching_record_count).isEqualTo(1001)
      assertThat(run.precomputing_done).isEqualTo(false)
      assertThat(run.state).isEqualTo(BackfillState.RUNNING)
    }

    runner = leaseHunter.hunt().single()

    partition = status.partitions.find { it.id == runner.partitionId.id }!!
    assertThat(partition.pkey_cursor).isNull()
    assertThat(partition.state).isEqualTo(BackfillState.RUNNING)

    runBlockingTestCancellable {
      runner.start(this)
    }

    status = getBackfillStatusAction.status(runner.backfillRunId.id)
    // All partitions complete.
    assertThat(status.state).isEqualTo(BackfillState.COMPLETE)
    partition = status.partitions.find { it.id == runner.partitionId.id }!!
    assertThat(partition.state).isEqualTo(BackfillState.COMPLETE)
    assertThat(partition.pkey_cursor).isEqualTo("1000")
    assertThat(partition.precomputing_pkey_cursor).isEqualTo("1000")
    assertThat(partition.precomputing_done).isEqualTo(true)
    assertThat(partition.computed_scanned_record_count).isEqualTo(1001)
    assertThat(partition.computed_matching_record_count).isEqualTo(1001)
    assertThat(partition.backfilled_scanned_record_count).isEqualTo(1001)
    assertThat(partition.backfilled_matching_record_count).isEqualTo(1001)

    assertThat(status.event_logs[0].message).isEqualTo("backfill completed")

    with(getBackfillRunsAction.backfillRuns("deep-fryer")) {
      assertThat(running_backfills).hasSize(0)
      assertThat(paused_backfills).hasSize(1)
      val run = paused_backfills.single()
      assertThat(run.backfilled_matching_record_count).isEqualTo(2002)
      assertThat(run.computed_matching_record_count).isEqualTo(2002)
      assertThat(run.precomputing_done).isEqualTo(true)
      assertThat(run.state).isEqualTo(BackfillState.COMPLETE)
    }
  }

  // An important use case is serial / single thread backfills.
  // Make sure we only invoke one and wait for it.
  @Test fun onlyOneParallelRpc() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1)

    runBlockingTestCancellable {
      runner.start(this)

      // We should only get numthreads=1 calls in parallel, then it must wait for more room.
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()

      // After one rpc completes, another one is enqueued.
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      assertThat(
        fakeBackfilaClientServiceClient.runBatchRequests.receive()
      ).isNotNull()

      runner.stop()
    }
  }

  @Test fun parametersAreSupplied() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1)

    runBlockingTestCancellable {
      runner.start(this)
      val firstRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      assertThat(firstRequest.parameters).containsEntry("cheese", "cheddar".encodeUtf8())
      runner.stop()
    }
  }

  @Test fun `batch size is supplied`() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1)
    scope.fakeCaller(user = "molly") {
      updateBackfillAction.update(
        runner.backfillRunId.id,
        UpdateBackfillRequest(batch_size = 10)
      )
    }

    runBlockingTestCancellable {
      runner.start(this)

      val firstRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      assertThat(firstRequest.batch_size).isEqualTo(10)
      runner.stop()
    }
  }

  @Test fun parallelCallsLimited() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 3)

    runBlockingTestCancellable {
      runner.start(this)

      // We should only get numthreads=3 calls in parallel, then it must wait for more room.
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()

      with(getBackfillRunsAction.backfillRuns("deep-fryer")) {
        assertThat(running_backfills).hasSize(1)
        assertThat(paused_backfills).hasSize(0)
        val run = running_backfills.single()
        assertThat(run.backfilled_matching_record_count).isEqualTo(0)
        assertThat(run.computed_matching_record_count).isEqualTo(1001)
        assertThat(run.precomputing_done).isEqualTo(false)
        assertThat(run.state).isEqualTo(BackfillState.RUNNING)
      }

      // After one rpc completes, another one is enqueued.
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
      runner.stop()
    }

    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("99".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }

    with(getBackfillRunsAction.backfillRuns("deep-fryer")) {
      assertThat(running_backfills).hasSize(1)
      assertThat(paused_backfills).hasSize(0)
      val run = running_backfills.single()
      assertThat(run.backfilled_matching_record_count).isEqualTo(100)
      assertThat(run.computed_matching_record_count).isEqualTo(1001)
      assertThat(run.precomputing_done).isEqualTo(false)
      assertThat(run.state).isEqualTo(BackfillState.RUNNING)
    }
  }

  // Make sure we are truly concurrent, i.e. we can process a RunBatch response even if
  // GetNextBatch is awaiting.
  @Test fun processRunBatchWhileGetNextBatchWaiting() {
    val runner = startBackfill(numThreads = 1)

    // Disable precomputing to avoid making interfering calls to GetNextBatch
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      partition.precomputing_done = true
    }

    runBlockingTestCancellable {
      runner.start(this)

      assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()).isNotNull()
      fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(
        Result.success(
          nextBatchResponse(start = "0", end = "99", scannedCount = 100, matchingCount = 100)
        )
      )
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()

      // Make this request wait
      assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()).isNotNull()

      // Complete the RunBatch
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )

      runner.stop()
    }

    // Cursor should be updated by RunBatch
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("99".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  // The cursor for GetNextBatch should be ahead of the DB cursor.
  @Test fun getNextBatchCursorSeparateFromDb() {
    val runner = startBackfill(numThreads = 1)

    // Disable precomputing to avoid making interfering calls to GetNextBatch
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      partition.precomputing_done = true
    }

    runBlockingTestCancellable {
      launch { runner.start(this) }

      // Process 4 getNextBatchRangeRequests
      assertThat(
        fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive().previous_end_key
      ).isNull()
      fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(
        Result.success(
          nextBatchResponse(start = "0", end = "99", scannedCount = 100, matchingCount = 100)
        )
      )

      assertThat(
        fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive().previous_end_key
      ).isEqualTo("99".encodeUtf8())
      fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(
        Result.success(
          nextBatchResponse(start = "100", end = "199", scannedCount = 100, matchingCount = 100)
        )
      )
      assertThat(
        fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive().previous_end_key
      ).isEqualTo("199".encodeUtf8())
      fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(
        Result.success(
          nextBatchResponse(start = "200", end = "299", scannedCount = 100, matchingCount = 100)
        )
      )
      assertThat(
        fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive().previous_end_key
      ).isEqualTo("299".encodeUtf8())
      fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(
        Result.success(
          nextBatchResponse(start = "300", end = "399", scannedCount = 100, matchingCount = 100)
        )
      )
      // Check that the channel where we sent the responses is empty.
      assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeResponses.poll()).isNull()
      // We will no longer get getNextBatchRequests until some batches are run.
      assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.poll()).isNull()

      // Run a RunBatch
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )

      // We should immediately get another RunBatch to run.
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()

      delay(EXTEND_LEASE_PERIOD.toMillis())

      // Cursor should be updated by RunBatch
      transacter.transaction { session ->
        val partition = session.load(runner.partitionId)
        assertThat(partition.pkey_cursor).isEqualTo("99".encodeUtf8())
        assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
      }

      // After the RunBatch completed, a getNextBatch request is buffered.
      // The next batch request should start higher than the pkey cursor in db
      assertThat(
        fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive().previous_end_key
      ).isEqualTo("399".encodeUtf8())

      // Complete the RunBatch
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      runner.stop()
    }
  }

  @Test fun stopThroughApi() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1)

    runBlockingTestCancellable {
      launch { runner.start(this) }
      // Leave awaiting run batch response
      fakeBackfilaClientServiceClient.runBatchRequests.receive()

      scope.fakeCaller(user = "molly") {
        stopBackfillAction.stop(runner.backfillRunId.id, StopBackfillRequest())
      }

      // Give enough time for the once-per-second check to cancel the jobs.
      delay(EXTEND_LEASE_PERIOD.toMillis())

      fakeBackfilaClientServiceClient.runBatchResponses.offer(
        Result.success(RunBatchResponse.Builder().build())
      )
    }

    // RunBatch was not awaited.
    // TODO: change expectation when we clean this up gracefully.
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isNull()
      assertThat(partition.run_state).isEqualTo(BackfillState.PAUSED)
    }
  }

  // Batches are processed in order even when one fails
  @Test fun failedBatchRetriedAfterBackoff() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 2)

    runBlockingTestCancellable {
      launch { runner.start(this) }

      val firstRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      fakeBackfilaClientServiceClient.runBatchRequests.receive()

      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.failure(RuntimeException("fake rpc failed"))
      )
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )

      delay(500)
      // Nothing sent yet - the backoff is 1000ms
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
      delay(500)
      val retry = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      assertThat(retry.batch_range).isEqualTo(firstRequest.batch_range)

      // Cursor is not updated, because the first rpc didn't succeed
      transacter.transaction { session ->
        val partition = session.load(runner.partitionId)
        assertThat(partition.pkey_cursor).isNull()
        assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
      }

      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      runner.stop()
    }
    // Cursor updated
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("199".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  @Test fun retriesExhaustedPausesBackfill() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1)

    runBlockingTestCancellable {
      launch { runner.start(this) }

      val firstRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()

      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.failure(RuntimeException("fake rpc failed"))
      )

      delay(500)
      // Nothing sent yet - the backoff is 1000ms
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
      fakeClock.add(1000, TimeUnit.MILLISECONDS)
      delay(500)
      val retry = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      assertThat(retry.batch_range).isEqualTo(firstRequest.batch_range)

      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.failure(RuntimeException("fake rpc failed"))
      )
      runner.stop()
    }

    val status = getBackfillStatusAction.status(runner.backfillRunId.id)
    val partition = status.partitions.find { it.id == runner.partitionId.id }!!
    // Cursor not updated, backfill paused
    assertThat(partition.pkey_cursor).isNull()
    assertThat(partition.state).isEqualTo(BackfillState.PAUSED)

    assertThat(status.event_logs[0].message).isEqualTo(
      "error running batch [0, 99], RPC error after 0ms. paused backfill due to 2 consecutive errors"
    )
    assertThat(status.event_logs[1].message).isEqualTo(
      "error running batch [0, 99], RPC error after 0ms. backing off for 1000ms"
    )
  }

  @Test fun `fails batch when stack trace is returned`() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 2)

    runBlockingTestCancellable {
      launch { runner.start(this) }

      val firstRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      fakeBackfilaClientServiceClient.runBatchRequests.receive()

      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(
          RunBatchResponse.Builder()
            .exception_stack_trace("fake stacktrace")
            .build()
        )
      )
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )

      delay(500)
      // Nothing sent yet - the backoff is 1000ms
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
      delay(500)
      val retry = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      assertThat(retry.batch_range).isEqualTo(firstRequest.batch_range)

      // Cursor is not updated, because the first rpc didn't succeed
      transacter.transaction { session ->
        val partition = session.load(runner.partitionId)
        assertThat(partition.pkey_cursor).isNull()
        assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
      }

      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      delay(EXTEND_LEASE_PERIOD.toMillis())
      runner.stop()
    }
    val status = getBackfillStatusAction.status(runner.backfillRunId.id)
    val partition = status.partitions.find { it.id == runner.partitionId.id }!!
    // Cursor updated
    assertThat(partition.pkey_cursor).isEqualTo("199")
    assertThat(partition.state).isEqualTo(BackfillState.RUNNING)

    assertThat(status.event_logs[0].message).isEqualTo(
      "error running batch [0, 99], client exception after 0ms. backing off for 1000ms"
    )
    assertThat(status.event_logs[0].extra_data).isEqualTo("fake stacktrace")
  }

  @Test fun skipEmptyBatches() {
    val runner = startBackfill(numThreads = 1)

    // Disable precomputing to avoid making interfering calls to GetNextBatch
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      partition.precomputing_done = true
    }

    runBlockingTestCancellable {
      launch { runner.start(this) }

      assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()).isNotNull()
      fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(
        Result.success(
          nextBatchResponse(start = "0", end = "99", scannedCount = 100, matchingCount = 0)
        )
      )

      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
      runner.stop()
    }

    // No RunBatch RPC was made but cursor should be updated by RunBatch
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("99".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  @Test fun backoffRequestedByClient() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1)

    runBlockingTestCancellable {
      launch { runner.start(this) }

      fakeBackfilaClientServiceClient.runBatchRequests.receive()
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(
          RunBatchResponse.Builder()
            .backoff_ms(1_000L).build()
        )
      )

      delay(500)
      // Nothing sent yet - the backoff is 1000ms
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
      delay(500)
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNotNull()
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      runner.stop()
    }
    // Cursor updated
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("199".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  @Test fun backoffOnSuccessfulRequests() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1, extraSleepMs = 1000L)

    runBlockingTestCancellable {
      launch { runner.start(this) }

      fakeBackfilaClientServiceClient.runBatchRequests.receive()
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )

      delay(500)
      // Nothing sent yet - the backoff is 1000ms
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
      delay(500)
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNotNull()
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      runner.stop()
    }
    // Cursor updated
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("199".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  @Test fun numThreadsIncreased() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 3)

    runBlockingTestCancellable {
      launch { runner.start(this) }

      // We should only get numthreads=3 calls in parallel, then it must wait for more room.
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()

      scope.fakeCaller(user = "molly") {
        updateBackfillAction.update(
          runner.backfillRunId.id,
          UpdateBackfillRequest(num_threads = 4)
        )
      }
      // Wait for leasing task to reload metadata.
      delay(EXTEND_LEASE_PERIOD.toMillis())

      // It takes 2 rpcs for the channels to resize, then another 3 are enqueued.
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
      runner.stop()
    }

    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("199".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  @Test fun `returning remaining batch range makes followup runbatch`() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1)

    runBlockingTestCancellable {
      launch { runner.start(this) }

      val initialRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()

      // Managed to complete the first item only
      val newStart = initialRequest.batch_range.start.utf8().toInt() + 1
      var remainingRange = initialRequest.batch_range.newBuilder()
        .start(newStart.toString().encodeUtf8())
        .build()
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().remaining_batch_range(remainingRange).build())
      )

      // Follow up request is sent immediately
      val followup1 = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      assertThat(followup1).isNotNull()

      // Batch ranges should be the remaining range
      assertThat(remainingRange).isEqualTo(followup1.batch_range)

      // Now we complete the last item in the batch
      val newEnd = followup1.batch_range.end.utf8().toInt() - 1
      remainingRange = followup1.batch_range.newBuilder()
        .end(newEnd.toString().encodeUtf8())
        .build()
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().remaining_batch_range(remainingRange).build())
      )

      // Follow up request is sent immediately
      val followup2 = fakeBackfilaClientServiceClient.runBatchRequests.receive()

      // Batch range is updated to the new remainingRange
      assertThat(followup2.batch_range).isEqualTo(remainingRange)

      // Cursor hasn't been updated yet
      delay(EXTEND_LEASE_PERIOD.toMillis())
      assertThat(getSinglePartitionCursor(runner)).isNull()

      // But that second follow up fails
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(
          RunBatchResponse.Builder()
            .exception_stack_trace("It Failed")
            .build()
        )
      )

      // Retry request is sent after the error backoff
      val retry = fakeBackfilaClientServiceClient.runBatchRequests.receive()

      // Batch range retried with the same remaining range.
      assertThat(retry.batch_range).isEqualTo(remainingRange)

      // Cursor hasn't been updated yet
      delay(EXTEND_LEASE_PERIOD.toMillis())
      assertThat(getSinglePartitionCursor(runner)).isNull()

      // Batch is done
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )

      // Cursor is updated once the batch succeeds
      delay(EXTEND_LEASE_PERIOD.toMillis())
      assertThat(getSinglePartitionCursor(runner))
        .isEqualTo(initialRequest.batch_range.end.utf8().toLong())

      val nextBatch = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      // Batch ranges are no longer the same
      assertThat(initialRequest.batch_range).isNotEqualTo(nextBatch.batch_range)
      runner.stop()
    }
  }

  @Test fun `remaining batch range reduces parallelism`() {
    // This is desired behaviour so backfila quickly backs off when encountering the unexpected.
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 3)

    runBlockingTestCancellable {
      launch { runner.start(this) }

      // We should only get numthreads=3 calls in parallel, then it must wait for more room.
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
      val initialRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      assertThat(initialRequest).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
      // Nothing has completed so the cursor has not been set yet
      assertThat(getSinglePartitionCursor(runner)).isNull()

      // The second request sends a partial result and the rest return normally
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )

      delay(EXTEND_LEASE_PERIOD.toMillis())
      val firstBatchCursor = getSinglePartitionCursor(runner)
      checkNotNull(firstBatchCursor) // cursor was set when the first batch succeeded

      // return a remaining batch
      val newStart = initialRequest.batch_range.start.utf8().toInt() + 1
      val remainingRange = initialRequest.batch_range.newBuilder()
        .start(newStart.toString().encodeUtf8())
        .build()
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().remaining_batch_range(remainingRange).build())
      )

      delay(EXTEND_LEASE_PERIOD.toMillis())
      assertThat(getSinglePartitionCursor(runner)).isEqualTo(firstBatchCursor) // cursor is unchanged
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )

      delay(EXTEND_LEASE_PERIOD.toMillis())
      assertThat(getSinglePartitionCursor(runner)).isEqualTo(firstBatchCursor) // cursor is unchanged

      // Only two requests are sent. The first is in response to the first batch succeeding and
      // the second is the follow up request
      val queuedRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      assertThat(queuedRequest).isNotNull()
      val followupRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()
      assertThat(remainingRange).isEqualTo(followupRequest.batch_range)

      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()

      // complete the two stuck requests currently being processed
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      delay(EXTEND_LEASE_PERIOD.toMillis())
      assertThat(getSinglePartitionCursor(runner)).isEqualTo(firstBatchCursor) // cursor is still unchanged
      fakeBackfilaClientServiceClient.runBatchResponses.send(
        Result.success(RunBatchResponse.Builder().build())
      )
      delay(EXTEND_LEASE_PERIOD.toMillis())
      val cursor = getSinglePartitionCursor(runner)
      assertThat(cursor).isGreaterThan(firstBatchCursor) // cursor increased
      assertThat(cursor).isEqualTo(queuedRequest.batch_range.end.utf8().toLong())

      // And we can get another 3
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull
      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull

      assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
      runner.stop()
    }
  }

  private fun getSinglePartitionCursor(runner: BackfillRunner): Long? {
    val currentPartitionStatus = getBackfillStatusAction
      .status(runner.backfillRunId.id)
      .partitions.find { it.id == runner.partitionId.id }!!
    return currentPartitionStatus.pkey_cursor?.toLong()
  }

  private fun startBackfill(numThreads: Int = 3, extraSleepMs: Long = 0): BackfillRunner {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich",
                "Description",
                listOf(Parameter("cheese", "cheddar or american")),
                null,
                null,
                false,
                null
              )
            )
          )
          .connector_type(ENVOY)
          .build()
      )
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .num_threads(numThreads)
          .backoff_schedule("1000")
          .extra_sleep_ms(extraSleepMs)
          .parameter_map(mapOf("cheese" to "cheddar".encodeUtf8()))
          .build()
      )
      val id = response.backfill_run_id
      startBackfillAction.start(id, StartBackfillRequest())
    }

    return leaseHunter.hunt().single()
  }

  private fun nextBatchResponse(
    start: String,
    end: String,
    scannedCount: Long,
    matchingCount: Long
  ) = GetNextBatchRangeResponse(
    listOf(
      GetNextBatchRangeResponse.Batch(
        KeyRange(start.encodeUtf8(), end.encodeUtf8()),
        scannedCount,
        matchingCount
      )
    )
  )
}
