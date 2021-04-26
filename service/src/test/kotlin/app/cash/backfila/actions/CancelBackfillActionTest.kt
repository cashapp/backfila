package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.FakeBackfilaClientServiceClient
import app.cash.backfila.dashboard.CancelBackfillAction
import app.cash.backfila.dashboard.CancelBackfillRequest
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.protos.service.Parameter
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillPartitionState
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.RunPartitionQuery
import com.google.inject.Module
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
import kotlin.test.assertNotNull

@MiskTest(startService = true)
class CancelBackfillActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var createBackfillAction: CreateBackfillAction

  @Inject
  lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject
  lateinit var cancelBackfillAction: CancelBackfillAction

  @Inject
  lateinit var scope: ActionScope

  @Inject
  lateinit var queryFactory: Query.Factory

  @Inject
  @BackfilaDb
  lateinit var transacter: Transacter

  @Inject
  lateinit var fakeBackfilaClientServiceClient: FakeBackfilaClientServiceClient

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

  @Test
  fun `cancel newly created backfill`() {
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
      assertThat(partitions[0].partition_name).isEqualTo("-80")
      assertThat(partitions[0].lease_token).isNull()
      assertThat(partitions[0].partition_state).isEqualTo(BackfillPartitionState.CANCELLED)
      assertThat(partitions[1].partition_name).isEqualTo("80-")
      assertThat(partitions[1].lease_token).isNull()
      assertThat(partitions[1].partition_state).isEqualTo(BackfillPartitionState.CANCELLED)
    }
  }

  BREAKING CHANGE
  TO tell us to add more tests.
  1 - progress has been made but still running both get cancelled
  2 - double cancelling ? what does it do?
  3 - one partition is complete. It should stay complete.
  4 - Add a stale test that will turn into a split partition test later that shows that stale does not change either.
}
