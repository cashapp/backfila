package app.cash.backfila.actions

import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.FakeBackfilaCallbackConnector
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.EditPartitionCursorHandlerAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.dashboard.StopBackfillRequest
import app.cash.backfila.dashboard.ViewDashboardUrlProvider
import app.cash.backfila.dashboard.ViewLogsUrlProvider
import app.cash.backfila.development.DevelopmentViewDashboardUrlProvider
import app.cash.backfila.development.DevelopmentViewLogsUrlProvider
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.runner.BackfillRunner
import app.cash.backfila.service.runner.EXTEND_LEASE_PERIOD
import app.cash.backfila.service.scheduler.LeaseHunter
import app.cash.backfila.ui.pages.BackfillShowAction
import com.google.inject.Module
import jakarta.inject.Inject
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import misk.MiskCaller
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.inject.toKey
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.FakeHttpCall
import misk.web.HttpCall
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class EditPartitionRangeEndRunnerTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(EditPartitionCursorActionTest.TestModule())
      bind<ViewLogsUrlProvider>().toInstance(DevelopmentViewLogsUrlProvider())
      bind<ViewDashboardUrlProvider>().toInstance(DevelopmentViewDashboardUrlProvider())
    }
  }

  @Inject lateinit var configureServiceAction: ConfigureServiceAction

  @Inject lateinit var createBackfillAction: CreateBackfillAction

  @Inject lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject lateinit var editPartitionCursorHandlerAction: EditPartitionCursorHandlerAction

  @Inject lateinit var startBackfillAction: StartBackfillAction

  @Inject lateinit var stopBackfillAction: StopBackfillAction

  @Inject lateinit var fakeClient: FakeBackfilaCallbackConnector

  @Inject lateinit var leaseHunter: LeaseHunter

  @Inject lateinit var scope: ActionScope

  @Inject lateinit var backfillShowAction: BackfillShowAction

  @Inject @BackfilaDb
  lateinit var transacter: Transacter

  @Test
  fun `pausing a live runner does not allow edits before its lease is released`() {
    val runner = createRunner(cursor = 99)

    runTest {
      runner.start(this)
      // Keep the runner in an RPC until its next lease check observes the pause.
      val request = fakeClient.getNextBatchRangeRequests.receive()
      assertThat(request.backfill_range.end).isEqualTo("1000".encodeUtf8())
      pause(runner)

      val response = inScope {
        editPartitionCursorHandlerAction.get(
          runner.backfillRunId.id, runner.partitionId.id, "99", "150", "199", "1000",
        )
      }

      assertThat(response.statusCode).isEqualTo(200)
      val partition = partition(runner)
      assertThat(partition.state).isEqualTo(BackfillState.PAUSED)
      assertThat(partition.pkey_cursor).isEqualTo("99")
      assertThat(partition.pkey_end).isEqualTo("1000")
      assertThat(partition.precomputing_done).isTrue()
      assertThat(partition.precomputing_pkey_cursor).isEqualTo("1000")
      assertThat(partition.computed_scanned_record_count).isEqualTo(1001)
      assertThat(partition.computed_matching_record_count).isEqualTo(1001)
      assertThat(partition.backfilled_matching_record_count).isEqualTo(100)

      delay(EXTEND_LEASE_PERIOD.toMillis())
    }
    runner.clearLease()
  }

  @Test
  fun `a stopped runner resumes scanning and precomputing with the narrowed end`() {
    resumeWithNarrowedEnd(cursor = 99)
  }

  @Test
  fun `resuming completes when the saved cursor is already past the narrowed end`() {
    resumeWithNarrowedEnd(cursor = 299)
  }

  @Test
  fun `resuming waits for recounting when scanning is already past the narrowed end`() {
    resumeWithNarrowedEnd(cursor = 299, precomputingDelayMs = 3 * EXTEND_LEASE_PERIOD.toMillis())
  }

  @Test
  fun `a runner waiting for recounting can still be paused`() {
    val runner = createRunner(cursor = 299)
    pause(runner)
    runner.clearLease()
    val response = inScope {
      editPartitionCursorHandlerAction.get(
        runner.backfillRunId.id, runner.partitionId.id, "299", null, "199", "1000",
      )
    }
    assertThat(response.statusCode).isEqualTo(303)
    scope.fakeCaller(user = "molly") {
      startBackfillAction.start(runner.backfillRunId.id, StartBackfillRequest())
    }
    val resumedRunner = leaseHunter.hunt().single()
    fakeClient.dontBlockGetNextBatch()
    fakeClient.dontBlockRunBatch()
    fakeClient.beforeGetNextBatchRange = { request ->
      if (request.precomputing == true) awaitCancellation()
    }

    runTest {
      resumedRunner.start(this)
      delay(3 * EXTEND_LEASE_PERIOD.toMillis())
      assertThat(partition(runner).state).isEqualTo(BackfillState.RUNNING)
      assertThat(partition(runner).precomputing_done).isFalse()
      pause(resumedRunner)
    }
    resumedRunner.clearLease()

    val status = getBackfillStatusAction.status(runner.backfillRunId.id)
    assertThat(status.state).isEqualTo(BackfillState.PAUSED)
    assertThat(status.partitions.single().state).isEqualTo(BackfillState.PAUSED)
    assertThat(status.partitions.single().precomputing_done).isFalse()
  }

  private fun resumeWithNarrowedEnd(cursor: Long, precomputingDelayMs: Long = 0) {
    val runner = createRunner(cursor)
    runTest {
      runner.start(this)
      fakeClient.getNextBatchRangeRequests.receive()
      pause(runner)
      delay(EXTEND_LEASE_PERIOD.toMillis())
    }
    // start() is the coroutine test entry point; run() normally releases this lease.
    runner.clearLease()

    val response = inScope {
      editPartitionCursorHandlerAction.get(
        runner.backfillRunId.id, runner.partitionId.id, cursor.toString(), null, "199", "1000",
      )
    }
    assertThat(response.statusCode).isEqualTo(303)
    with(partition(runner)) {
      assertThat(pkey_cursor).isEqualTo(cursor.toString())
      assertThat(precomputing_done).isFalse()
      assertThat(precomputing_pkey_cursor).isNull()
      assertThat(computed_scanned_record_count).isZero()
      assertThat(computed_matching_record_count).isZero()
      assertThat(backfilled_scanned_record_count).isEqualTo(cursor + 1)
      assertThat(backfilled_matching_record_count).isEqualTo(cursor + 1)
    }

    scope.fakeCaller(user = "molly") {
      startBackfillAction.start(runner.backfillRunId.id, StartBackfillRequest())
    }
    val resumedRunner = leaseHunter.hunt().single()
    val rangeRequests = mutableListOf<GetNextBatchRangeRequest>()
    val runRequests = mutableListOf<RunBatchRequest>()

    fakeClient.beforeGetNextBatchRange = { request ->
      if (request.precomputing == true) delay(precomputingDelayMs)
    }

    runTest {
      backgroundScope.launch {
        for (request in fakeClient.getNextBatchRangeRequests) {
          rangeRequests.add(request)
          fakeClient.getNextBatchRangeResponses.send(Result.success(nextBatch(request)))
        }
      }
      backgroundScope.launch {
        for (request in fakeClient.runBatchRequests) {
          runRequests.add(request)
          fakeClient.runBatchResponses.send(Result.success(RunBatchResponse.Builder().build()))
        }
      }
      resumedRunner.start(this)
    }
    resumedRunner.clearLease()

    assertThat(rangeRequests).isNotEmpty()
    assertThat(rangeRequests.map { it.backfill_range.end }).containsOnly("199".encodeUtf8())
    val precomputingRequests = rangeRequests.filter { it.precomputing == true }
    val scanningRequests = rangeRequests.filter { it.precomputing != true }
    assertThat(precomputingRequests).isNotEmpty()
    assertThat(precomputingRequests.first().previous_end_key).isNull()
    assertThat(scanningRequests).isNotEmpty()
    assertThat(scanningRequests.first().previous_end_key).isEqualTo(cursor.toString().encodeUtf8())

    if (cursor < 199) {
      assertThat(runRequests.map { it.batch_range })
        .containsExactly(KeyRange("100".encodeUtf8(), "199".encodeUtf8()))
    } else {
      assertThat(runRequests).isEmpty()
    }
    val status = getBackfillStatusAction.status(runner.backfillRunId.id)
    assertThat(status.state).isEqualTo(BackfillState.COMPLETE)
    with(status.partitions.single()) {
      assertThat(state).isEqualTo(BackfillState.COMPLETE)
      assertThat(pkey_cursor).isEqualTo(maxOf(cursor, 199).toString())
      assertThat(precomputing_done).isTrue()
      assertThat(precomputing_pkey_cursor).isEqualTo("199")
      assertThat(computed_scanned_record_count).isEqualTo(200)
      assertThat(computed_matching_record_count).isEqualTo(200)
      assertThat(backfilled_scanned_record_count).isEqualTo(maxOf(cursor + 1, 200))
      assertThat(backfilled_matching_record_count).isEqualTo(maxOf(cursor + 1, 200))
    }
    inScope {
      val page = backfillShowAction.get(runner.backfillRunId.id)
      val html = Buffer().also { page.body.writeTo(it) }.readUtf8()
      assertThat(html).contains("data-progress-pct=\"100.0\"")
      assertThat(html).doesNotContain("width: 150.0%", ">150%<")
      val overallProgress = Regex("""Overall Progress</span>\s*<span[^>]*>([^<]+)</span>""")
        .find(html)!!.groupValues[1]
      assertThat(overallProgress).isEqualTo("100.0%")
    }
  }

  private fun nextBatch(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    val start = request.previous_end_key?.utf8()?.toLong()?.plus(1)
      ?: request.backfill_range.start.utf8().toLong()
    val rangeEnd = request.backfill_range.end.utf8().toLong()
    if (start > rangeEnd) return GetNextBatchRangeResponse(emptyList())
    val end = minOf(start + request.batch_size - 1, rangeEnd)
    return GetNextBatchRangeResponse(
      listOf(
        GetNextBatchRangeResponse.Batch(
          KeyRange(start.toString().encodeUtf8(), end.toString().encodeUtf8()),
          end - start + 1,
          end - start + 1,
        ),
      ),
    )
  }

  private fun pause(runner: BackfillRunner) = scope.fakeCaller(user = "molly") {
    stopBackfillAction.stop(runner.backfillRunId.id, StopBackfillRequest())
  }

  private fun partition(runner: BackfillRunner) =
    getBackfillStatusAction.status(runner.backfillRunId.id).partitions.single()

  private fun createRunner(cursor: Long): BackfillRunner {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
    fakeClient.prepareBackfillResponses.add(
      PrepareBackfillResponse.Builder()
        .partitions(
          listOf(
            PrepareBackfillResponse.Partition(
              "only", KeyRange("0".encodeUtf8(), "1000".encodeUtf8()), null,
            ),
          ),
        )
        .build(),
    )
    scope.fakeCaller(user = "molly") {
      val id = createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .num_threads(1)
          .batch_size(100)
          .build(),
      ).backfill_run_id
      startBackfillAction.start(id, StartBackfillRequest())
    }
    return leaseHunter.hunt().single().also { runner ->
      transacter.transaction { session ->
        val partition = session.load(runner.partitionId)
        partition.pkey_cursor = cursor.toString().encodeUtf8()
        partition.precomputing_done = true
        partition.precomputing_pkey_cursor = "1000".encodeUtf8()
        partition.computed_scanned_record_count = 1001
        partition.computed_matching_record_count = 1001
        partition.backfilled_scanned_record_count = cursor + 1
        partition.backfilled_matching_record_count = cursor + 1
      }
    }
  }

  private fun <T> inScope(function: () -> T): T = scope.create(
    mapOf(
      keyOf<MiskCaller>() to MiskCaller(user = "molly"),
      HttpCall::class.toKey() to FakeHttpCall(
        url = "http://backfila/api/backfill/1/partitions/1/cursor".toHttpUrl(),
      ),
    ),
  ).inScope(function)
}
