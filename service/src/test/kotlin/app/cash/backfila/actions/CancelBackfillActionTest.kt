package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.FakeBackfilaClientServiceClient
import app.cash.backfila.dashboard.CancelBackfillAction
import app.cash.backfila.dashboard.CancelBackfillRequest
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.protos.service.CreateBackfillResponse
import app.cash.backfila.protos.service.Parameter
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillPartitionState
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.RunPartitionQuery
import app.cash.backfila.service.scheduler.LeaseHunter
import com.google.inject.Module
import misk.exceptions.BadRequestException
import javax.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull

@MiskTest(startService = true)
class CancelBackfillActionTest {
  @Suppress("unused")
  @MiskTestModule val module: Module = BackfilaTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction
  @Inject lateinit var createBackfillAction: CreateBackfillAction
  @Inject lateinit var startBackfillAction: StartBackfillAction
  @Inject lateinit var stopBackfillAction: StopBackfillAction
  @Inject lateinit var getBackfillStatusAction: GetBackfillStatusAction
  @Inject lateinit var cancelBackfillAction: CancelBackfillAction

  @Inject lateinit var scope: ActionScope
  @Inject lateinit var leaseHunter: LeaseHunter

  @Inject @BackfilaDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Inject lateinit var fakeBackfilaClientServiceClient: FakeBackfilaClientServiceClient

  @BeforeEach
  fun setup() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData.Builder()
                .name("ChickenSandwich")
                .parameters(
                  listOf(
                    Parameter.Builder()
                      .name("param1")
                      .build()
                  )
                )
                .build()
            )
          )
          .connector_type(Connectors.ENVOY)
          .build()
      )
    }
  }

  @Test fun `cancel newly created backfill`() {
    val createdBackfill = createBackfillRun(false)

    scope.fakeCaller(user = "molly") {
      cancelBackfillAction.cancel(
        createdBackfill.backfill_run_id,
        CancelBackfillRequest()
      )
    }

    transacter.transaction { session ->
      val run = queryFactory.newQuery<BackfillRunQuery>().uniqueResult(session)
      assertNotNull(run)
      assertThat(run.state).isEqualTo(BackfillState.CANCELLED)
      assertThat(run.created_by_user).isEqualTo("molly")
      assertThat(createdBackfill.backfill_run_id).isEqualTo(run.id.id)

      val partitions = queryFactory.newQuery<RunPartitionQuery>()
        .backfillRunId(run.id)
        .orderByName()
        .list(session)
      assertThat(partitions).hasSize(2)
      assertThat(partitions).allSatisfy {
        assertThat(it.partition_state).isEqualTo(BackfillPartitionState.CANCELLED)
        assertThat(it.lease_token).isNull()
      }
    }
  }

  @Test fun `cannot cancel running backfill`() {
    val createdBackfill = createBackfillRun(true)

    val exception = assertThrows<BadRequestException> {
      scope.fakeCaller(user = "molly") {
        cancelBackfillAction.cancel(
          createdBackfill.backfill_run_id,
          CancelBackfillRequest()
        )
      }
    }
    assertThat(exception).hasMessageEndingWith("isn't PAUSED, can't move to state CANCELLED")
  }

  @Test fun `cancel stopped in progress backfill`() {
    val createdBackfill = createBackfillRun(true)

    val exception = assertThrows<BadRequestException> {
      scope.fakeCaller(user = "molly") {
        cancelBackfillAction.cancel(
          createdBackfill.backfill_run_id,
          CancelBackfillRequest()
        )
      }
    }
    assertThat(exception).hasMessageEndingWith("isn't PAUSED, can't move to state CANCELLED")
  }

  private fun createBackfillRun(startBackfill: Boolean): CreateBackfillResponse {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false
              )
            )
          )
          .connector_type(Connectors.ENVOY)
          .build()
      )
    }
    val createdBackfill = scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )
    }

    if (startBackfill) {
      scope.fakeCaller(user = "molly") {
        startBackfillAction.start(createdBackfill.backfill_run_id, StartBackfillRequest())
      }
      leaseHunter.hunt().single()
    }
    return createdBackfill
  }
/*
  BREAKING CHANGE
  TO tell us to add more tests.
  1 - progress has been made but still running both get cancelled
  2 - double cancelling ? what does it do?
  3 - one partition is complete. It should stay complete.
  4 - Add a stale test that will turn into a split partition test later that shows that stale does not change either. */
}
