package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.dashboard.StopBackfillRequest
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import com.google.inject.Module
import jakarta.inject.Inject
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.hibernate.newQuery
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class GetBackfillRunsActionFilteredTest {
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
  lateinit var scope: ActionScope

  @Inject
  lateinit var clock: FakeClock

  @Inject
  lateinit var queryFactory: Query.Factory

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
                null, false, null, null,
              ),
              ConfigureServiceRequest.BackfillData(
                "TurkeySandwich", "Description", listOf(), null,
                null, false, null, null,
              ),
              ConfigureServiceRequest.BackfillData(
                "FrenchFries", "Description", listOf(), null,
                null, false, null, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
  }

  @Test
  fun `recent backfills are limited to the exact creator and newest ten non-deleted runs`() {
    fun create(user: String): Long = scope.fakeCaller(user = user) {
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder().backfill_name("ChickenSandwich").build(),
      ).backfill_run_id
    }

    val ownIds = (1..12).map { create("molly") }
    create("molly.chen")
    create("someone-else")
    val deletedId = create("molly")
    transacter.transaction { session ->
      session.load<DbBackfillRun>(Id(deletedId)).deleted_at = clock.instant()
    }

    val recent = getBackfillRunsAction.recentBackfills("molly")
    assertThat(recent.map { it.id }).containsExactlyElementsOf(ownIds.takeLast(10).reversed().map { it.toString() })
    assertThat(recent.map { it.created_by_user }).containsOnly("molly")
  }

  @Test
  fun `recent backfills include all states and services with progress and creation time`() {
    scope.fakeCaller(service = "bakery") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(listOf(ConfigureServiceRequest.BackfillData.Builder().name("Croissant").build()))
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
    val states = BackfillState.values().toList()
    val ids = states.mapIndexed { index, state ->
      val service = if (index % 2 == 0) "deep-fryer" else "bakery"
      val name = if (service == "bakery") "Croissant" else "ChickenSandwich"
      val id = scope.fakeCaller(user = "molly") {
        createBackfillAction.create(
          service,
          RESERVED_VARIANT,
          CreateBackfillRequest.Builder().backfill_name(name).build(),
        ).backfill_run_id
      }
      transacter.transaction { session ->
        val run = session.load<DbBackfillRun>(Id(id))
        run.setState(session, queryFactory, state)
        run.partitions(session, queryFactory).forEach {
          it.precomputing_done = true
          it.computed_matching_record_count = 100
          it.backfilled_matching_record_count = 25
        }
      }
      id.toString()
    }

    val recent = getBackfillRunsAction.recentBackfills("molly")
    assertThat(recent.map { it.id }).containsExactlyElementsOf(ids.reversed())
    assertThat(recent.map { it.state }).containsExactlyElementsOf(states.reversed())
    assertThat(recent.map { it.name }).contains("Croissant", "ChickenSandwich")
    assertThat(recent).allSatisfy {
      assertThat(it.created_at).isEqualTo(clock.instant())
      assertThat(it.precomputing_done).isTrue()
      assertThat(it.computed_matching_record_count).isGreaterThan(0)
      assertThat(it.backfilled_matching_record_count * 4).isEqualTo(it.computed_matching_record_count)
    }
  }

  @Test
  fun `recent backfills are empty for a user with no runs or no username`() {
    assertThat(getBackfillRunsAction.recentBackfills("new-user")).isEmpty()
    assertThat(getBackfillRunsAction.recentBackfills("")).isEmpty()
  }

  @Test
  fun `search by backfill name`() {
    scope.fakeCaller(user = "molly") {
      val createBackfillResponse = createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
      val backfillId = createBackfillResponse.backfill_run_id

      var backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer", RESERVED_VARIANT)
      with(backfillRuns) {
        assertThat(running_backfills).hasSize(0)
        assertThat(paused_backfills.single().id).isEqualTo(backfillId.toString())
      }

      startBackfillAction.start(backfillId, StartBackfillRequest())

      backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer", RESERVED_VARIANT)
      with(backfillRuns) {
        assertThat(running_backfills.single().id).isEqualTo(backfillId.toString())
        assertThat(paused_backfills).hasSize(0)
      }

      var backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        backfill_name = "ChickenSandwich",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(1)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        backfill_name = "Chicken",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(1)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        backfill_name = "Sandwich",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(1)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        backfill_name = "nonexistingname",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)

      stopBackfillAction.stop(backfillId, StopBackfillRequest())
      backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        backfill_name = "ChickenSandwich",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)
    }
  }

  @Test
  fun `search for deactivated backfill name`() {
    scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      var backfillRuns = getBackfillRunsAction.backfillRuns("deep-fryer", RESERVED_VARIANT)
      assertThat(backfillRuns.paused_backfills).hasSize(1)
      assertThat(backfillRuns.running_backfills).hasSize(0)

      // deactivate backfill
      transacter.transaction { session ->
        val registeredBackfill = queryFactory.newQuery<RegisteredBackfillQuery>()
          .name("ChickenSandwich")
          .list(session)
          .first()
        registeredBackfill.deactivate(clock)
        session.save(registeredBackfill)
      }

      // search for deactivated backfill
      var backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "ChickenSandwich",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)
    }
  }

  @Test
  fun `search by author`() {
    scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      var backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        created_by_user = "molly",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        created_by_user = "fakeUserName",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)
      assertThat(backfillSearchResults.paused_backfills).hasSize(0)
    }
  }

  @Test
  fun `multiple criteria search`() {
    scope.fakeCaller(user = "diana") {
      val response = createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
    }
    scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("FrenchFries")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("FrenchFries")
          .build(),
      )

      var backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "FrenchFries",
        created_by_user = "molly",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(2)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        created_by_user = "molly",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(3)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        backfill_name = "FrenchFries",
        created_by_user = "diana",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(0)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
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
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("TurkeySandwich")
          .build(),
      )
    }
    scope.fakeCaller(user = "molly.chen") {
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("FrenchFries")
          .build(),
      )
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("FrenchFries")
          .build(),
      )

      var backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        created_by_user = "molly",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(5)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "Sandwich",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(3)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "Sandwich",
        created_by_user = "baker",
      )
      assertThat(backfillSearchResults.paused_backfills).hasSize(2)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
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
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      var backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
        backfill_name = "",
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)

      backfillSearchResults = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        pagination_token = null,
      )
      assertThat(backfillSearchResults.running_backfills).hasSize(0)
      assertThat(backfillSearchResults.paused_backfills).hasSize(1)
    }
  }

  @Test
  fun `pagination with filter`() {
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
                .build(),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
    scope.fakeCaller(user = "molly") {
      repeat(30) {
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
            .backfill_name("BeefSandwich")
            .build(),
        )
      }
      val backfillRuns = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        backfill_name = "ChickenSandwich",
      )
      assertThat(backfillRuns.paused_backfills).hasSize(20)

      val backfillRunsPage2 = getBackfillRunsAction.backfillRuns(
        service = "deep-fryer",
        variant = RESERVED_VARIANT,
        backfill_name = "ChickenSandwich",
        pagination_token = backfillRuns.next_pagination_token,
      )
      assertThat(backfillRunsPage2.paused_backfills).hasSize(10)
    }
  }
}
