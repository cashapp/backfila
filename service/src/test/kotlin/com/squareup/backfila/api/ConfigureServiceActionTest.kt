package com.squareup.backfila.api

import com.google.inject.Module
import com.squareup.backfila.BackfilaTestingModule
import com.squareup.backfila.client.Connectors.ENVOY
import com.squareup.backfila.client.Connectors.HTTP
import com.squareup.backfila.dashboard.GetRegisteredBackfillsAction
import com.squareup.backfila.fakeCaller
import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.RegisteredBackfillQuery
import com.squareup.backfila.service.ServiceQuery
import com.squareup.moshi.JsonEncodingException
import com.squareup.protos.backfila.service.ConfigureServiceRequest
import com.squareup.protos.backfila.service.Parameter
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@MiskTest(startService = true)
class ConfigureServiceActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject lateinit var clock: Clock
  @Inject lateinit var configureServiceAction: ConfigureServiceAction
  @Inject lateinit var getRegisteredBackfillsAction: GetRegisteredBackfillsAction
  @Inject @BackfilaDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var scope: ActionScope

  @Test
  fun changeServiceConnector() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .connector_type(ENVOY)
          .connector_extra_data("{\"clusterType\":\"production\"}")
          .build())
      assertThat(backfillNames("deep-fryer")).isEmpty()

      transacter.transaction { session ->
        val dbService = queryFactory.newQuery<ServiceQuery>()
            .registryName("deep-fryer")
            .uniqueResult(session)!!
        assertThat(dbService.connector).isEqualTo(ENVOY)
        assertThat(dbService.connector_extra_data).isEqualTo("{\"clusterType\":\"production\"}")
      }

      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .connector_type(HTTP)
          .connector_extra_data("{\"clusterType\":\"staging\"}")
          .build())
      assertThat(backfillNames("deep-fryer")).isEmpty()

      transacter.transaction { session ->
        val dbService = queryFactory.newQuery<ServiceQuery>()
            .registryName("deep-fryer")
            .uniqueResult(session)!!
        assertThat(dbService.connector).isEqualTo(HTTP)
        assertThat(dbService.connector_extra_data).isEqualTo("{\"clusterType\":\"staging\"}")
      }
    }
  }

  @Test
  fun configureServiceSyncsBackfills() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
          ConfigureServiceRequest.Builder()
              .connector_type(ENVOY)
              .build())
      assertThat(backfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, null,
                  null)))
          .connector_type(ENVOY)
          .build())
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("zzz", "Description", listOf(), null, null,
                  false)))
          .connector_type(ENVOY)
          .build())
      assertThat(backfillNames("deep-fryer")).containsOnly("zzz")
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")
    }
  }

  @Test
  fun deleteOneOfTwo() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, null,
                  false),
              ConfigureServiceRequest.BackfillData("zzz", "Description", listOf(), null, null,
                  false)))
          .connector_type(ENVOY)
          .build())
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz", "zzz")
      assertThat(deletedBackfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("zzz", "Description", listOf(), null, null,
                  false)))
          .connector_type(ENVOY)
          .build())
      assertThat(backfillNames("deep-fryer")).containsOnly("zzz")
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")
    }
  }

  @Test
  fun reintroduceBackfill() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .connector_type(ENVOY)
          .build())
      assertThat(backfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, null,
                  false)))
          .connector_type(ENVOY)
          .build())
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .connector_type(ENVOY)
          .build())
      assertThat(backfillNames("deep-fryer")).isEmpty()
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")

      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, null,
                  false)))
          .connector_type(ENVOY)
          .build())
      // The reintroduced backfill is created, and the old one kept around.
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")
    }
  }

  @Test
  fun modifyBackfillRequiresApprovalBackAndForth() {
    scope.fakeCaller(service = "deep-fryer") {
      // Going back and forth between approval required creates new registered backfills.
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, null,
                  false)))
          .connector_type(ENVOY)
          .build())
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, null,
                  true)))
          .connector_type(ENVOY)
          .build())
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")

      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, null,
                  false)))
          .connector_type(ENVOY)
          .build())
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
          Backfill("xyz", clock.instant(), type_provided = null, type_consumed = null,
              parameter_names = null, requires_approval = false),
          Backfill("xyz", clock.instant(), type_provided = null, type_consumed = null,
              parameter_names = null, requires_approval = true),
          Backfill("xyz", null, type_provided = null, type_consumed = null,
              parameter_names = null, requires_approval = false)
      )
    }
  }

  @Test
  fun modifyParams() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, null,
                  false)))
          .connector_type(ENVOY)
          .build())
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description",
                  listOf(Parameter.Builder().name("abc").build()),
                  null, null, false)))
          .connector_type(ENVOY)
          .build())
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
          Backfill("xyz", clock.instant(), type_provided = null, type_consumed = null,
              parameter_names = null, requires_approval = false),
          Backfill("xyz", null, type_provided = null, type_consumed = null,
              parameter_names = "abc", requires_approval = false)
      )
    }
  }

  @Test
  fun modifyTypeProvided() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), "String", null,
                  false)))
          .connector_type(ENVOY)
          .build())
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), "Int", null,
                  false)))
          .connector_type(ENVOY)
          .build())
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
          Backfill("xyz", clock.instant(), type_provided = "String", type_consumed = null,
              parameter_names = null, requires_approval = false),
          Backfill("xyz", null, type_provided = "Int", type_consumed = null,
              parameter_names = null, requires_approval = false)
      )
    }
  }

  @Test
  fun modifyTypeConsumed() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, "String",
                  false)))
          .connector_type(ENVOY)
          .build())
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, "Int",
                  false)))
          .connector_type(ENVOY)
          .build())
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
          Backfill("xyz", clock.instant(), type_provided = null, type_consumed = "String",
              parameter_names = null, requires_approval = false),
          Backfill("xyz", null, type_provided = null, type_consumed = "Int",
              parameter_names = null, requires_approval = false)
      )
    }
  }

  @Test
  fun unchangedBackfillNotDuplicated() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, "String",
                  false)))
          .connector_type(ENVOY)
          .build())
      configureServiceAction.configureService(ConfigureServiceRequest.Builder()
          .backfills(listOf(
              ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, "String",
                  false)))
          .connector_type(ENVOY)
          .build())
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
          Backfill("xyz", deleted_in_service_at = null, type_provided = null,
              type_consumed = "String", parameter_names = null, requires_approval = false)
      )
    }
  }

  @Test
  fun invalidConnectorExtraData() {
    scope.fakeCaller(service = "deep-fryer") {
      assertThatThrownBy {
        configureServiceAction.configureService(ConfigureServiceRequest.Builder()
            .backfills(listOf(
                ConfigureServiceRequest.BackfillData("xyz", "Description", listOf(), null, "String",
                    false)))
            .connector_type(ENVOY)
            .connector_extra_data("poop")
            .build())
      }.isInstanceOf(JsonEncodingException::class.java)
    }
  }

  private fun backfillNames(serviceName: String): List<String> {
    return getRegisteredBackfillsAction.backfills(serviceName)
        .backfills.map { it.name }
  }

  private fun deletedBackfillNames(serviceName: String): List<String> {
    return transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
          .registryName(serviceName)
          .uniqueResult(session)!!
      queryFactory.newQuery<RegisteredBackfillQuery>()
          .serviceId(dbService.id)
          .notActive()
          .list(session)
          .map { it.name }
    }
  }

  data class Backfill(
    val name: String,
    val deleted_in_service_at: Instant?,
    val type_provided: String?,
    val type_consumed: String?,
    val parameter_names: String?,
    val requires_approval: Boolean
  )

  private fun backfills(serviceName: String): List<Backfill> {
    return transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
          .registryName(serviceName)
          .uniqueResult(session)!!
      queryFactory.newQuery<RegisteredBackfillQuery>()
          .serviceId(dbService.id)
          .list(session)
          .map {
            Backfill(
                it.name,
                it.deleted_in_service_at,
                it.type_provided,
                it.type_consumed,
                it.parameter_names,
                it.requires_approval
            )
          }
    }
  }
}
