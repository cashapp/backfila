package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.DeleteServiceVariantAction
import app.cash.backfila.dashboard.GetServiceVariantsAction
import app.cash.backfila.dashboard.GetServicesAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import com.google.inject.Module
import javax.inject.Inject
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
class DeleteServiceVariantActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction
  @Inject lateinit var deleteServiceVariantAction: DeleteServiceVariantAction
  @Inject lateinit var getServicesAction: GetServicesAction
  @Inject lateinit var getServiceVariantsAction: GetServiceVariantsAction
  @Inject lateinit var createBackfillAction: CreateBackfillAction
  @Inject @BackfilaDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var scope: ActionScope

  @Test
  fun `delete a variant with no running backfills`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(listOf())
          .connector_type(Connectors.ENVOY)
          .variant("playpen-jackf")
          .build(),
      )
    }

    scope.fakeCaller(user = "molly") {
      deleteServiceVariantAction.delete("deep-fryer", "playpen-jackf")
    }

    val variants = scope.fakeCaller(user = "molly") {
      getServiceVariantsAction.variants("deep-fryer")
    }
    assertThat(variants.variants.map { it.name }).doesNotContain("playpen-jackf")
  }

  @Test
  fun `deleted variant does not appear in services list`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(listOf())
          .connector_type(Connectors.ENVOY)
          .variant("playpen-jackf")
          .build(),
      )
    }

    scope.fakeCaller(user = "molly") {
      deleteServiceVariantAction.delete("deep-fryer", "playpen-jackf")
    }

    val services = scope.fakeCaller(user = "molly") {
      getServicesAction.services()
    }
    val deepFryer = services.services.find { it.name == "deep-fryer" }
    assertThat(deepFryer?.variants).doesNotContain("playpen-jackf")
  }

  @Test
  fun `cannot delete a non-existent variant`() {
    scope.fakeCaller(user = "molly") {
      assertThatThrownBy {
        deleteServiceVariantAction.delete("deep-fryer", "playpen-jackf")
      }.isInstanceOf(BadRequestException::class.java)
    }
  }

  @Test
  fun `cannot delete an already-deleted variant`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(listOf())
          .connector_type(Connectors.ENVOY)
          .variant("playpen-jackf")
          .build(),
      )
    }

    scope.fakeCaller(user = "molly") {
      deleteServiceVariantAction.delete("deep-fryer", "playpen-jackf")
    }

    scope.fakeCaller(user = "molly") {
      assertThatThrownBy {
        deleteServiceVariantAction.delete("deep-fryer", "playpen-jackf")
      }.isInstanceOf(BadRequestException::class.java)
    }
  }

  @Test
  fun `cannot delete a variant with running backfills`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .variant("playpen-jackf")
          .build(),
      )
    }

    val response = scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        "playpen-jackf",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
    }

    transacter.transaction { session ->
      val backfill = session.load<DbBackfillRun>(Id(response.backfill_run_id))
      backfill.setState(session, queryFactory, BackfillState.RUNNING)
    }

    scope.fakeCaller(user = "molly") {
      assertThatThrownBy {
        deleteServiceVariantAction.delete("deep-fryer", "playpen-jackf")
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessageContaining("running backfill")
    }
  }

  @Test
  fun `re-registering a deleted variant creates a fresh row`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(listOf())
          .connector_type(Connectors.ENVOY)
          .variant("playpen-jackf")
          .build(),
      )
    }

    scope.fakeCaller(user = "molly") {
      deleteServiceVariantAction.delete("deep-fryer", "playpen-jackf")
    }

    // Re-register the same variant - should succeed (creates new row)
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(listOf())
          .connector_type(Connectors.ENVOY)
          .variant("playpen-jackf")
          .build(),
      )
    }

    val variants = scope.fakeCaller(user = "molly") {
      getServiceVariantsAction.variants("deep-fryer")
    }
    assertThat(variants.variants.map { it.name }).contains("playpen-jackf")
  }
}
