package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.FakeBackfilaClientServiceClient
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.dashboard.StopBackfillRequest
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillPartitionState
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import app.cash.backfila.softAssert
import app.cash.backfila.uiEventLogWith
import com.google.inject.Module
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertNotNull

@MiskTest(startService = true)
class StartStopBackfillActionTest {
  @Suppress("unused")
  @MiskTestModule val module: Module = BackfilaTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction

  @Inject lateinit var createBackfillAction: CreateBackfillAction

  @Inject lateinit var startBackfillAction: StartBackfillAction

  @Inject lateinit var stopBackfillAction: StopBackfillAction

  @Inject lateinit var getBackfillRunsAction: GetBackfillRunsAction

  @Inject lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject lateinit var queryFactory: Query.Factory

  @Inject lateinit var scope: ActionScope

  @Inject @BackfilaDb lateinit var transacter: Transacter

  @Inject lateinit var fakeBackfilaClientServiceClient: FakeBackfilaClientServiceClient

  @BeforeEach
  fun setup() {
    // Always configure the backfills before any test
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData.Builder()
                .name("ChickenSandwich")
                .description("Description")
                .build(),
              ConfigureServiceRequest.BackfillData.Builder()
                .name("BeefSandwich")
                .description("Description")
                .build()
            )
          )
          .connector_type(Connectors.ENVOY)
          .build()
      )
    }
  }

  @Test fun `Backfila starts with no backfills`() {
    scope.fakeCaller(user = "molly") {
      var backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer")
      softAssert {
        assertThat(backfillRuns.paused_backfills).hasSize(0)
        assertThat(backfillRuns.running_backfills).hasSize(0)
      }
    }
  }

  @Nested inner class `After a backfill is created` {
    var createdBackfillRunId: Long = 0 // lateinit won't work on primitives

    @BeforeEach fun createBackfill() {
      scope.fakeCaller(user = "molly") {
        val response = createBackfillAction.create(
          "deep-fryer",
          CreateBackfillRequest.Builder()
            .backfill_name("ChickenSandwich")
            .build()
        )
        createdBackfillRunId = response.backfill_run_id
      }
    }

    @Test fun `The backfill starts out paused`() {
      scope.fakeCaller(user = "molly") {
        var backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer")
        softAssert {
          assertThat(backfillRuns.paused_backfills).hasSize(1)
          assertThat(backfillRuns.running_backfills).hasSize(0)
          assertThat(backfillRuns.paused_backfills).singleElement().extracting { it.id }
            .isEqualTo(createdBackfillRunId.toString())
        }
      }
    }

    @Nested inner class `And then the backfill is started` {
      @BeforeEach fun startBackfill() {
        scope.fakeCaller(user = "molly") {
          startBackfillAction.start(createdBackfillRunId, StartBackfillRequest())
        }
      }

      @Test fun `the Backfill is running`() {
        softAssert {
          var status = getBackfillStatusAction.status(createdBackfillRunId)
          assertThat(status.state).isEqualTo(BackfillState.RUNNING)
          assertThat(status.partitions).extracting<BackfillPartitionState> { it.state }
            .containsOnly(BackfillPartitionState.RUNNING)

          assertThat(status.event_logs).first().`is`(
            uiEventLogWith(
              type = DbEventLog.Type.STATE_CHANGE,
              user = "molly",
              message = "backfill started"
            )
          )

          val backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer")
          assertThat(backfillRuns.paused_backfills).hasSize(0)
          assertThat(backfillRuns.running_backfills).hasSize(1)
        }
      }

      @Nested inner class `And then the backfill is stopped` {
        @BeforeEach fun stopBackfill() {
          scope.fakeCaller(user = "molly") {
            stopBackfillAction.stop(createdBackfillRunId, StopBackfillRequest())
          }
        }

        @Test fun `the Backfill is stopped`() {
          softAssert {
            val status = getBackfillStatusAction.status(createdBackfillRunId)
            assertThat(status.state).describedAs("state").isEqualTo(BackfillState.PAUSED)
            assertThat(status.partitions.map { it.state })
                .containsOnly(BackfillPartitionState.PAUSED)

            Assertions.assertThat(status.event_logs).first().`is`(
                uiEventLogWith(
                    type = DbEventLog.Type.STATE_CHANGE,
                    user = "molly",
                    message = "backfill stopped"
                )
            )

            val backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer")
            assertThat(backfillRuns.paused_backfills).hasSize(1)
            assertThat(backfillRuns.running_backfills).hasSize(0)
          }
        }
      }
    }
  }

  @Test
  fun pagination() {
    scope.fakeCaller(user = "molly") {
      repeat(15) {
        createBackfillAction.create(
          "deep-fryer",
          CreateBackfillRequest.Builder()
            .backfill_name("ChickenSandwich")
            .build()
        )
        createBackfillAction.create(
          "deep-fryer",
          CreateBackfillRequest.Builder()
            .backfill_name("BeefSandwich")
            .build()
        )
      }
      val backfillRunsPage1 = getBackfillRunsAction.backfillRuns("deep-fryer")
      assertThat(backfillRunsPage1.paused_backfills).hasSize(20)

      val backfillRunsPage2 = getBackfillRunsAction.backfillRuns(
        "deep-fryer",
        pagination_token = backfillRunsPage1.next_pagination_token
      )
      assertThat(backfillRunsPage2.paused_backfills).hasSize(10)
    }
  }

  @Test
  fun `an incorrect backfill does not exist`() {
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )
      val incorrectId = response.backfill_run_id + 1

      assertThatThrownBy {
        startBackfillAction.start(incorrectId, StartBackfillRequest())
      }.isInstanceOf(BadRequestException::class.java)
    }
  }

  @Test
  fun cantStartRunningBackfill() {
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )
      val id = response.backfill_run_id
      startBackfillAction.start(id, StartBackfillRequest())

      transacter.transaction { session ->
        val run = session.load(Id<DbBackfillRun>(id))
        assertNotNull(run)
        assertThat(run.state).isEqualTo(BackfillState.RUNNING)
      }

      assertThatThrownBy {
        startBackfillAction.start(id, StartBackfillRequest())
      }.isInstanceOf(BadRequestException::class.java)
    }
  }

  @Test
  fun cantStopPausedBackfill() {
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )
      val id = response.backfill_run_id
      assertThatThrownBy {
        stopBackfillAction.stop(id, StopBackfillRequest())
      }.isInstanceOf(BadRequestException::class.java)
    }
  }

  @Test
  fun cantToggleCompletedBackfill() {
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )
      val id = response.backfill_run_id

      transacter.transaction { session ->
        val run = session.load(Id<DbBackfillRun>(id))
        assertNotNull(run)
        run.setState(session, queryFactory, BackfillState.COMPLETE)
      }

      assertThatThrownBy {
        startBackfillAction.start(id, StartBackfillRequest())
      }.isInstanceOf(BadRequestException::class.java)
      assertThatThrownBy {
        stopBackfillAction.stop(id, StopBackfillRequest())
      }.isInstanceOf(BadRequestException::class.java)
    }
  }
}
