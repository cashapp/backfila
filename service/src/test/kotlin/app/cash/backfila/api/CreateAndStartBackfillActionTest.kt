package app.cash.backfila.api

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import com.google.inject.Module
import javax.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class CreateAndStartBackfillActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var createAndStartBackfillAction: CreateAndStartBackfillAction

  @Inject
  lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject
  lateinit var scope: ActionScope

  @Inject
  lateinit var queryFactory: Query.Factory

  @Inject
  @BackfilaDb
  lateinit var transacter: Transacter

  @Test
  fun `create and start`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null
              )
            )
          )
          .connector_type(Connectors.ENVOY)
          .build()
      )
    }
    scope.fakeCaller(service = "deep-fryer") {
      val response = createAndStartBackfillAction.createAndStartBackfill(
        CreateAndStartBackfillRequest.Builder()
          .create_request(
            CreateBackfillRequest.Builder()
              .backfill_name("ChickenSandwich")
              .build()
          )
          .build()
      )

      val status = getBackfillStatusAction.status(response.backfill_run_id)
      assertThat(status.state).isEqualTo(BackfillState.RUNNING)
      assertThat(status.created_by_user).isEqualTo("deep-fryer")
      assertThat(status.partitions[0].name).isEqualTo("-80")
      assertThat(status.partitions[0].state).isEqualTo(BackfillState.RUNNING)
      assertThat(status.partitions[1].name).isEqualTo("80-")
      assertThat(status.partitions[1].state).isEqualTo(BackfillState.RUNNING)
    }
  }
}
