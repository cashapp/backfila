package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.SearchBackfillRunsAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.dashboard.StopAllBackfillsAction
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.dashboard.StopBackfillRequest
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import com.google.inject.Module
import javax.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.temporal.TemporalAmount

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
  lateinit var clock: FakeClock

  @Inject
  @BackfilaDb
  lateinit var transacter: Transacter

  @BeforeEach
  fun setup() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null,
              ),
              ConfigureServiceRequest.BackfillData(
                "TurkeySandwich", "Description", listOf(), null,
                null, false, null,
              ),
              ConfigureServiceRequest.BackfillData(
                "FrenchFries", "Description", listOf(), null,
                null, false, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
  }

  @Test
  fun `search by backfil name`() {
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

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "ChickenSandwich",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)
    }
  }

  @Test
  fun `search by author`() {
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      var backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        created_by_user = "molly",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        created_by_user = "fakeUserName",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)
      assertThat(backfillSearchResults.paused_backfills).hasSize(0)
    }
  }

  @Test
  fun `search by date`() {
    scope.fakeCaller(user = "molly") {

      val backfillStartTime1 = clock.instant()

      val response = createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      clock.add(Duration.ofDays(1))
      val backfillStartTime2 = clock.instant()
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("FrenchFries")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("FrenchFries")
          .build(),
      )
      var backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        created_start_date = backfillStartTime1,
        created_end_date = backfillStartTime1.plus(Duration.ofSeconds(1)),
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)


      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        created_start_date = backfillStartTime1,
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(3)

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        created_end_date = backfillStartTime1,
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)

    }
  }

  @Test
  fun `multiple criteria search`() {
    scope.fakeCaller(user = "diana") {
      val response = createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
    }
    scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("FrenchFries")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("FrenchFries")
          .build(),
      )

      var backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "FrenchFries",
        created_by_user = "molly",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(2)

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        created_by_user = "molly",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(3)

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        backfill_name = "FrenchFries",
        created_by_user = "diana",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(0)

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        backfill_name = "ChickenSandwich",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(2)
    }
  }

  @Test
  fun `fuzzy search`() {
    scope.fakeCaller(user = "molly.baker") {
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("TurkeySandwich")
          .build(),
      )
    }
    scope.fakeCaller(user = "molly.chen") {
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("FrenchFries")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("FrenchFries")
          .build(),
      )

      var backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        created_by_user = "molly",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(5)

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "Sandwich",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(3)

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "Sandwich",
        created_by_user = "baker",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(2)

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "sandwich",
        created_by_user = "chen",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(0)
    }
  }

  @Test
  fun `null and empty search queries are ignored`() {
    scope.fakeCaller(user = "molly") {
      var backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer", RESERVED_VARIANT)
      assertThat(backfillRuns.paused_backfills).hasSize(0)
      assertThat(backfillRuns.running_backfills).hasSize(0)

      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      var backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)

      backfillSearchResults = searchBackfillRunsAction.searchBackfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)
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
