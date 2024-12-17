package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.*
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import com.google.inject.Module
import javax.inject.Inject
import kotlin.test.assertNotNull
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class SearchBackfillRunsActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var createBackfillAction: CreateBackfillAction

  @Inject
  lateinit var startBackfillAction: StartBackfillAction

  @Inject
  lateinit var stopBackfillAction: StopBackfillAction

  @Inject
  lateinit var stopAllBackfillsAction: StopAllBackfillsAction

  @Inject
  lateinit var getBackfillRunsAction: GetBackfillRunsAction

  @Inject
  lateinit var searchBackfillRunsAction: SearchBackfillRunsAction

  @Inject
  lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject
  lateinit var queryFactory: Query.Factory

  @Inject
  lateinit var scope: ActionScope

  @Inject
  @BackfilaDb
  lateinit var transacter: Transacter

  @Test
  fun searchBackfillRuns() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
    scope.fakeCaller(user = "molly") {
      var backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer", RESERVED_VARIANT)
      assertThat(backfillRuns.paused_backfills).hasSize(0)
      assertThat(backfillRuns.running_backfills).hasSize(0)

      val response = createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer", RESERVED_VARIANT)
      assertThat(backfillRuns.paused_backfills).hasSize(1)
      assertThat(backfillRuns.running_backfills).hasSize(0)

      val id = response.backfill_run_id
      assertThat(backfillRuns.paused_backfills[0].id).isEqualTo(id.toString())
      startBackfillAction.start(id, StartBackfillRequest())

      backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer", RESERVED_VARIANT)
      assertThat(backfillRuns.paused_backfills).hasSize(0)
      assertThat(backfillRuns.running_backfills).hasSize(1)


      var backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "ChickenSandwich",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(1)

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "nonexistingname",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)



      stopBackfillAction.stop(id, StopBackfillRequest())

      backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer", RESERVED_VARIANT)
      assertThat(backfillRuns.paused_backfills).hasSize(1)
      assertThat(backfillRuns.running_backfills).hasSize(0)
    }
  }
//
//  @Test
//  fun pagination() {
//    scope.fakeCaller(service = "deep-fryer") {
//      configureServiceAction.configureService(
//        ConfigureServiceRequest.Builder()
//          .backfills(
//            listOf(
//              ConfigureServiceRequest.BackfillData.Builder()
//                .name("ChickenSandwich")
//                .description("Description")
//                .build(),
//              ConfigureServiceRequest.BackfillData.Builder()
//                .name("BeefSandwich")
//                .description("Description")
//                .build(),
//            ),
//          )
//          .connector_type(Connectors.ENVOY)
//          .build(),
//      )
//    }
//    scope.fakeCaller(user = "molly") {
//      repeat(15) {
//        createBackfillAction.create(
//          "deep-fryer",
//          ConfigureServiceAction.RESERVED_VARIANT,
//          CreateBackfillRequest.Builder()
//            .backfill_name("ChickenSandwich")
//            .build(),
//        )
//        createBackfillAction.create(
//          "deep-fryer",
//          ConfigureServiceAction.RESERVED_VARIANT,
//          CreateBackfillRequest.Builder()
//            .backfill_name("BeefSandwich")
//            .build(),
//        )
//      }
//      val backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer", RESERVED_VARIANT)
//      assertThat(backfillRuns.paused_backfills).hasSize(20)
//
//      val backfillRunsPage2 = getBackfillRunsAction.backfillRuns(
//        "deep-fryer",
//        RESERVED_VARIANT, pagination_token = backfillRuns.next_pagination_token,
//      )
//      assertThat(backfillRunsPage2.paused_backfills).hasSize(10)
//    }
//  }
}
