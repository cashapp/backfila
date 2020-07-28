package app.cash.backfila.service

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors.ENVOY
import app.cash.backfila.client.FakeBackfilaClientServiceClient
import app.cash.backfila.dashboard.CreateBackfillAction
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
import app.cash.backfila.service.scheduler.LeaseHunter
import com.google.inject.Module
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@MiskTest(startService = true)
class BackfillRunnerTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction
  @Inject lateinit var createBackfillAction: CreateBackfillAction
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

    runner.run()

    var status = getBackfillStatusAction.status(runner.backfillRunId.id)
    var partition = status.partitions.find { it.id == runner.partitionId.id }!!
    assertThat(partition.pkey_cursor).isEqualTo("1000")
    assertThat(partition.state).isEqualTo(BackfillState.COMPLETE)
    // Not all partitions complete.
    assertThat(status.state).isEqualTo(BackfillState.RUNNING)

    runner = leaseHunter.hunt().single()

    partition = status.partitions.find { it.id == runner.partitionId.id }!!
    assertThat(partition.pkey_cursor).isNull()
    assertThat(partition.state).isEqualTo(BackfillState.RUNNING)

    runner.run()

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
  }

  // An important use case is serial / single thread backfills.
  // Make sure we only invoke one and wait for it.
  @Test fun onlyOneParallelRpc() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1)

    runBlocking {
      launch { runner.run() }
      try {
        // We should only get numthreads=1 calls in parallel, then it must wait for more room.
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
        yield()
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()

        // After one rpc completes, another one is enqueued.
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))
        assertThat(withTimeoutOrNull(1000L) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }).isNotNull()
      } finally {
        runner.stop()
      }
    }
  }

  @Test fun parametersAreSupplied() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1)

    runBlocking {
      launch { runner.run() }
      try {
        val firstRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()
        assertThat(firstRequest.parameters).containsEntry("cheese", "cheddar".encodeUtf8())
      } finally {
        runner.stop()
      }
    }
  }

  @Test fun parallelCallsLimited() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 3)

    runBlocking {
      launch { runner.run() }
      try {
        // We should only get numthreads=3 calls in parallel, then it must wait for more room.
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
        yield()
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()

        // After one rpc completes, another one is enqueued.
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))
        assertThat(withTimeoutOrNull(1000L) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }).isNotNull()
      } finally {
        runner.stop()
      }
    }

    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("99".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
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

    runBlocking {
      launch { runner.run() }
      try {
        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()).isNotNull()
        fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(Result.success(
            nextBatchResponse(start = "0", end = "99", scannedCount = 100, matchingCount = 100)))
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()

        // Make this request wait
        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()).isNotNull()

        // Complete the RunBatch
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))
      } finally {
        runner.stop()
      }
    }

    // Cursor should be updated by RunBatch
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("99".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  // The cursor for GetNextBatch should be ahead of the DB.
  @Test fun getNextBatchCursorSeparateFromDb() {
    val runner = startBackfill(numThreads = 1)

    // Disable precomputing to avoid making interfering calls to GetNextBatch
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      partition.precomputing_done = true
    }

    runBlocking {
      launch { runner.run() }
      try {
        // 1 thread, so it should send 1 rpc and buffer 2 batches
        assertThat(
            fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive().previous_end_key).isNull()
        fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(Result.success(
            nextBatchResponse(start = "0", end = "99", scannedCount = 100, matchingCount = 100)))

        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()
            .previous_end_key).isEqualTo("99".encodeUtf8())
        fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(Result.success(
            nextBatchResponse(start = "100", end = "199", scannedCount = 100, matchingCount = 100)))
        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()
            .previous_end_key).isEqualTo("199".encodeUtf8())
        fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(Result.success(
            nextBatchResponse(start = "200", end = "299", scannedCount = 100, matchingCount = 100)))
        // Not buffering any more batches
        yield()
        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeResponses.poll()).isNull()

        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))

        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()

        // Cursor should be updated by RunBatch
        transacter.transaction { session ->
          val partition = session.load(runner.partitionId)
          assertThat(partition.pkey_cursor).isEqualTo("99".encodeUtf8())
          assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
        }

        // After the batch completed, another one is buffered.
        // The next batch request should start higher than the pkey cursor in db
        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()
            .previous_end_key).isEqualTo("299".encodeUtf8())

        // Complete the RunBatch
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))
      } finally {
        runner.stop()
      }
    }
  }

  @Test fun stopThroughApi() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 1)

    runBlocking {
      launch { runner.run() }
      // Leave awaiting run batch response
      fakeBackfilaClientServiceClient.runBatchRequests.receive()

      scope.fakeCaller(user = "molly") {
        stopBackfillAction.stop(runner.backfillRunId.id, StopBackfillRequest())
      }

      // Give enough time for the once-per-second check to cancel the jobs.
      delay(1100)

      fakeBackfilaClientServiceClient.runBatchResponses.offer(
          Result.success(RunBatchResponse.Builder().build()))
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

    runBlocking {
      launch { runner.run() }
      try {
        val firstRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()
        fakeBackfilaClientServiceClient.runBatchRequests.receive()

        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.failure(RuntimeException("fake rpc failed")))
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))

        delay(500)
        // Nothing sent yet - the backoff is 1000ms
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
        // Give a bit more buffer to send next request due to scheduling delays.
        val retry = withTimeout(2000) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }
        assertThat(retry.batch_range).isEqualTo(firstRequest.batch_range)

        // Cursor is not updated, because the first rpc didn't succeed
        transacter.transaction { session ->
          val partition = session.load(runner.partitionId)
          assertThat(partition.pkey_cursor).isNull()
          assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
        }

        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))
      } finally {
        runner.stop()
      }
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

    runBlocking {
      launch { runner.run() }
      try {
        val firstRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()

        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.failure(RuntimeException("fake rpc failed")))

        delay(500)
        // Nothing sent yet - the backoff is 1000ms
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
        fakeClock.add(1000, TimeUnit.MILLISECONDS)
        // Give a bit more buffer to send next request due to scheduling delays.
        val retry = withTimeout(2000) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }
        assertThat(retry.batch_range).isEqualTo(firstRequest.batch_range)

        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.failure(RuntimeException("fake rpc failed")))
      } finally {
        runner.stop()
      }
    }
    // Cursor not updated, backfill paused
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isNull()
      assertThat(partition.run_state).isEqualTo(BackfillState.PAUSED)
    }
  }

  @Test fun `fails batch when stack trace is returned`() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    val runner = startBackfill(numThreads = 2)

    runBlocking {
      launch { runner.run() }
      try {
        val firstRequest = fakeBackfilaClientServiceClient.runBatchRequests.receive()
        fakeBackfilaClientServiceClient.runBatchRequests.receive()

        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder()
                .exception_stack_trace("stacktrace")
                .build())
        )
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build())
        )

        delay(500)
        // Nothing sent yet - the backoff is 1000ms
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
        // Give a bit more buffer to send next request due to scheduling delays.
        val retry = withTimeout(2000) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }
        assertThat(retry.batch_range).isEqualTo(firstRequest.batch_range)

        // Cursor is not updated, because the first rpc didn't succeed
        transacter.transaction { session ->
          val partition = session.load(runner.partitionId)
          assertThat(partition.pkey_cursor).isNull()
          assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
        }

        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))
      } finally {
        runner.stop()
      }
    }
    // Cursor updated
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("199".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  @Test fun skipEmptyBatches() {
    val runner = startBackfill(numThreads = 1)

    // Disable precomputing to avoid making interfering calls to GetNextBatch
    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      partition.precomputing_done = true
    }

    runBlocking {
      launch { runner.run() }
      try {
        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()).isNotNull()
        fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(Result.success(
            nextBatchResponse(start = "0", end = "99", scannedCount = 100, matchingCount = 0)))

        assertThat(withTimeoutOrNull(1000L) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }).isNull()
      } finally {
        runner.stop()
      }
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

    runBlocking {
      launch { runner.run() }
      try {
        fakeBackfilaClientServiceClient.runBatchRequests.receive()
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder()
                .backoff_ms(1_000L).build()))

        delay(500)
        // Nothing sent yet - the backoff is 1000ms
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
        // Give a bit more buffer to send next request due to scheduling delays.
        assertThat(withTimeoutOrNull(2000) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }).isNotNull()
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))
      } finally {
        runner.stop()
      }
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

    runBlocking {
      launch { runner.run() }
      try {
        fakeBackfilaClientServiceClient.runBatchRequests.receive()
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))

        delay(500)
        // Nothing sent yet - the backoff is 1000ms
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()
        // Give a bit more buffer to send next request due to scheduling delays.
        assertThat(withTimeoutOrNull(2000) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }).isNotNull()
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))
      } finally {
        runner.stop()
      }
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

    runBlocking {
      launch { runner.run() }
      try {
        // We should only get numthreads=3 calls in parallel, then it must wait for more room.
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
        yield()
        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.poll()).isNull()

        updateBackfillAction.update(runner.backfillRunId.id,
            UpdateBackfillRequest(num_threads = 4))
        // Wait for runner to reload metadata.
        delay(2000)

        // It takes 2 rpcs for the channels to resize, then another 3 are enqueued.
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))

        assertThat(withTimeoutOrNull(1000L) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }).isNotNull()
        assertThat(withTimeoutOrNull(1000L) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }).isNotNull()
        assertThat(withTimeoutOrNull(1000L) {
          fakeBackfilaClientServiceClient.runBatchRequests.receive()
        }).isNotNull()
      } finally {
        runner.stop()
      }
    }

    transacter.transaction { session ->
      val partition = session.load(runner.partitionId)
      assertThat(partition.pkey_cursor).isEqualTo("199".encodeUtf8())
      assertThat(partition.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  private fun startBackfill(numThreads: Int = 3, extraSleepMs: Long = 0): BackfillRunner {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData(
                  "ChickenSandwich",
                  "Description",
                  listOf(Parameter("cheese", "cheddar or american")),
                  null,
                  null,
                  false
              )))
          .connector_type(ENVOY)
          .build())
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
