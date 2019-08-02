package com.squareup.backfila.service

import com.google.inject.Module
import com.squareup.backfila.BackfilaTestingModule
import com.squareup.backfila.api.ConfigureServiceAction
import com.squareup.backfila.client.FakeBackfilaClientServiceClient
import com.squareup.backfila.dashboard.CreateBackfillAction
import com.squareup.backfila.dashboard.CreateBackfillRequest
import com.squareup.backfila.dashboard.StartBackfillAction
import com.squareup.backfila.dashboard.StartBackfillRequest
import com.squareup.backfila.dashboard.StopBackfillAction
import com.squareup.backfila.dashboard.StopBackfillRequest
import com.squareup.backfila.fakeCaller
import com.squareup.protos.backfila.clientservice.RunBatchResponse
import com.squareup.protos.backfila.service.ConfigureServiceRequest
import com.squareup.protos.backfila.service.Connector
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
  @Inject lateinit var startBackfillAction: StartBackfillAction
  @Inject lateinit var stopBackfillAction: StopBackfillAction
  @Inject lateinit var scope: ActionScope
  @Inject lateinit var fakeClock: FakeClock
  @Inject lateinit var leaseHunter: LeaseHunter
  @Inject @BackfilaDb lateinit var transacter: Transacter
  @Inject lateinit var fakeBackfilaClientServiceClient: FakeBackfilaClientServiceClient

  @Test fun happyCompleteRun() {
    fakeBackfilaClientServiceClient.dontBlockGetNextBatch()
    fakeBackfilaClientServiceClient.dontBlockRunBatch()

    var runner = startBackfill()

    transacter.transaction { session ->
      val instance = session.load(runner.instanceId)
      assertThat(instance.pkey_cursor).isNull()
      assertThat(instance.run_state).isEqualTo(BackfillState.RUNNING)
    }

    runner.run()

    transacter.transaction { session ->
      val instance = session.load(runner.instanceId)
      assertThat(instance.pkey_cursor).isEqualTo("1000".encodeUtf8())
      assertThat(instance.run_state).isEqualTo(BackfillState.COMPLETE)
      // Not all instances complete.
      assertThat(instance.backfill_run.state).isEqualTo(BackfillState.RUNNING)
    }

    runner = leaseHunter.hunt().single()

    transacter.transaction { session ->
      val instance = session.load(runner.instanceId)
      assertThat(instance.pkey_cursor).isNull()
      assertThat(instance.run_state).isEqualTo(BackfillState.RUNNING)
    }

    runner.run()

    transacter.transaction { session ->
      val instance = session.load(runner.instanceId)
      assertThat(instance.pkey_cursor).isEqualTo("1000".encodeUtf8())
      assertThat(instance.precomputing_pkey_cursor).isEqualTo("1000".encodeUtf8())
      assertThat(instance.precomputing_done).isEqualTo(true)
      assertThat(instance.computed_scanned_record_count).isEqualTo(1001)
      assertThat(instance.computed_matching_record_count).isEqualTo(1001)
      assertThat(instance.backfilled_scanned_record_count).isEqualTo(1001)
      assertThat(instance.backfilled_matching_record_count).isEqualTo(1001)
      assertThat(instance.run_state).isEqualTo(BackfillState.COMPLETE)
      // All instances complete.
      assertThat(instance.backfill_run.state).isEqualTo(BackfillState.COMPLETE)
    }
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
      val instance = session.load(runner.instanceId)
      assertThat(instance.pkey_cursor).isEqualTo("99".encodeUtf8())
      assertThat(instance.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  // Make sure we are truly concurrent, i.e. we can process a RunBatch response even if
  // GetNextBatch is awaiting.
  @Test fun processRunBatchWhileGetNextBatchWaiting() {
    val runner = startBackfill(numThreads = 1)

    // Disable precomputing to avoid making interfering calls to GetNextBatch
    transacter.transaction { session ->
      val instance = session.load(runner.instanceId)
      instance.precomputing_done = true
    }

    runBlocking {
      launch { runner.run() }
      try {
        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()).isNotNull()
        fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(Result.success(Unit))
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
      val instance = session.load(runner.instanceId)
      assertThat(instance.pkey_cursor).isEqualTo("99".encodeUtf8())
      assertThat(instance.run_state).isEqualTo(BackfillState.RUNNING)
    }
  }

  // The cursor for GetNextBatch should be ahead of the DB.
  @Test fun getNextBatchCursorSeparateFromDb() {
    val runner = startBackfill(numThreads = 1)

    // Disable precomputing to avoid making interfering calls to GetNextBatch
    transacter.transaction { session ->
      val instance = session.load(runner.instanceId)
      instance.precomputing_done = true
    }

    runBlocking {
      launch { runner.run() }
      try {
        // 1 thread, so it should send 1 rpc and buffer 2 batches
        assertThat(
            fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive().previous_end_key).isNull()
        fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(Result.success(Unit))

        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()
            .previous_end_key).isEqualTo("99".encodeUtf8())
        fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(Result.success(Unit))
        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeRequests.receive()
            .previous_end_key).isEqualTo("199".encodeUtf8())
        fakeBackfilaClientServiceClient.getNextBatchRangeResponses.send(Result.success(Unit))
        // Not buffering any more batches
        yield()
        assertThat(fakeBackfilaClientServiceClient.getNextBatchRangeResponses.poll()).isNull()

        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()
        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))

        assertThat(fakeBackfilaClientServiceClient.runBatchRequests.receive()).isNotNull()

        // Cursor should be updated by RunBatch
        transacter.transaction { session ->
          val instance = session.load(runner.instanceId)
          assertThat(instance.pkey_cursor).isEqualTo("99".encodeUtf8())
          assertThat(instance.run_state).isEqualTo(BackfillState.RUNNING)
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
      val instance = session.load(runner.instanceId)
      assertThat(instance.pkey_cursor).isNull()
      assertThat(instance.run_state).isEqualTo(BackfillState.PAUSED)
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
          val instance = session.load(runner.instanceId)
          assertThat(instance.pkey_cursor).isNull()
          assertThat(instance.run_state).isEqualTo(BackfillState.RUNNING)
        }

        fakeBackfilaClientServiceClient.runBatchResponses.send(
            Result.success(RunBatchResponse.Builder().build()))
      } finally {
        runner.stop()
      }
    }
    // Cursor updated
    transacter.transaction { session ->
      val instance = session.load(runner.instanceId)
      assertThat(instance.pkey_cursor).isEqualTo("199".encodeUtf8())
      assertThat(instance.run_state).isEqualTo(BackfillState.RUNNING)
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
      val instance = session.load(runner.instanceId)
      assertThat(instance.pkey_cursor).isNull()
      assertThat(instance.run_state).isEqualTo(BackfillState.PAUSED)
    }
  }

  fun startBackfill(numThreads: Int = 3): BackfillRunner {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("ChickenSandwich", "Description", listOf(), null,
              null, false)),
          Connector.ENVOY, null))
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create("deep-fryer",
          CreateBackfillRequest("ChickenSandwich", num_threads = numThreads,
              backoff_schedule = "1000"))
      val id = response.headers["Location"]!!.substringAfterLast("/").toLong()
      startBackfillAction.start(id, StartBackfillRequest())
    }

    return leaseHunter.hunt().single()
  }
}
