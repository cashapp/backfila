package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillRunsAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StopBackfillAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.service.persistence.BackfilaDb
import com.google.inject.Module
import javax.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.junit.jupiter.api.BeforeEach

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

  // ... rest of the test file remains the same as it doesn't create BackfillData objects directly ...
}
