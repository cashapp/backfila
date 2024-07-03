package app.cash.backfila.service.selfbackfill

import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.Parameter
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import com.google.inject.Module
import javax.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class BackfillRegisteredParametersTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = SelfBackfillTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var scope: ActionScope

  @Inject
  @BackfilaDb
  lateinit var transacter: Transacter

  @Inject
  lateinit var queryFactory: Query.Factory

  @Inject
  lateinit var backfila: Backfila

  @Test
  fun `add missing parameter`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description",
                listOf(
                  Parameter("name", "desc", false),
                ),
                null, null, false, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }

    backfila.createWetRun<BackfillRegisteredParameters>().execute()

    transacter.transaction { session ->
      val backfill = queryFactory.newQuery<RegisteredBackfillQuery>()
        .uniqueResult(session)!!
      assertThat(backfill.parameters).hasSize(1)
      assertThat(backfill.parameters.single().name).isEqualTo("name")
      assertThat(backfill.parameters.single().description).isEqualTo("desc")
      assertThat(backfill.parameters.single().required).isFalse()
      backfill.parameters.forEach { session.delete(it) }
    }

    backfila.createWetRun<BackfillRegisteredParameters>().execute()

    transacter.transaction { session ->
      val backfill = queryFactory.newQuery<RegisteredBackfillQuery>()
        .uniqueResult(session)!!
      assertThat(backfill.parameters).hasSize(1)
      assertThat(backfill.parameters.single().name).isEqualTo("name")
      assertThat(backfill.parameters.single().description).isEqualTo(null)
      assertThat(backfill.parameters.single().required).isFalse()
    }
  }

  @Test
  fun `add missing multiple parameters`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description",
                listOf(
                  Parameter("abc", "desc1", false),
                  Parameter("def", "desc2", false),
                ),
                null, null, false, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }

    backfila.createWetRun<BackfillRegisteredParameters>().execute()

    transacter.transaction { session ->
      val backfill = queryFactory.newQuery<RegisteredBackfillQuery>()
        .uniqueResult(session)!!
      assertThat(backfill.parameters).hasSize(2)
      backfill.parameters.forEach { session.delete(it) }
    }

    backfila.createWetRun<BackfillRegisteredParameters>().execute()

    transacter.transaction { session ->
      val backfill = queryFactory.newQuery<RegisteredBackfillQuery>()
        .uniqueResult(session)!!
      assertThat(backfill.parameters).hasSize(2)
      assertThat(backfill.parameters.first().name).isEqualTo("abc")
      assertThat(backfill.parameters.first().description).isEqualTo(null)
      assertThat(backfill.parameters.first().required).isFalse()
      assertThat(backfill.parameters.last().name).isEqualTo("def")
      assertThat(backfill.parameters.last().description).isEqualTo(null)
      assertThat(backfill.parameters.last().required).isFalse()
    }
  }

  @Test
  fun `do nothing for backfill without parameters`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description",
                listOf(),
                null,
                null, false, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }

    backfila.createWetRun<BackfillRegisteredParameters>().execute()

    transacter.transaction { session ->
      val backfill = queryFactory.newQuery<RegisteredBackfillQuery>()
        .uniqueResult(session)!!
      assertThat(backfill.parameters).hasSize(0)
    }

    backfila.createWetRun<BackfillRegisteredParameters>().execute()

    transacter.transaction { session ->
      val backfill = queryFactory.newQuery<RegisteredBackfillQuery>()
        .uniqueResult(session)!!
      assertThat(backfill.parameters).hasSize(0)
    }
  }
}
