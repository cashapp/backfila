package app.cash.backfila

import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import jakarta.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.hibernate.pagination.idDescPaginator
import misk.hibernate.pagination.newPager
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class PaginationTest {
  @MiskTestModule
  private val module = BackfilaTestingModule()

  @Inject @BackfilaDb
  private lateinit var transacter: Transacter

  @Inject private lateinit var queryFactory: Query.Factory

  @Inject private lateinit var configureServiceAction: ConfigureServiceAction

  @Inject private lateinit var createBackfillAction: CreateBackfillAction

  @Inject private lateinit var scope: ActionScope

  @Test
  fun happyPath() {
    // Seed rows
    seedData()

    // Test pagination
    val allRows = transacter.transaction { session ->
      queryFactory.newQuery<BackfillRunQuery>()
        .list(session)
    }
    val rows = transacter.transaction { session ->
      val pager = queryFactory.newQuery<BackfillRunQuery>()
        .orderByUpdatedAtDesc()
        .newPager(idDescPaginator(), pageSize = 10)
      pager.nextPage(session)
        ?.contents
    }

    val a = "a"
  }

  fun seedData() {
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

    for (i in 0..100) {
      scope.fakeCaller(user = "molly") {
        val response = createBackfillAction.create(
          "deep-fryer",
          RESERVED_VARIANT,
          CreateBackfillRequest.Builder()
            .backfill_name("ChickenSandwich")
            .build(),
        )
      }
    }
  }
}
