package com.squareup.backfila.api

import com.google.inject.Key
import com.google.inject.Module
import com.squareup.backfila.actions.BackfilaWebActionTestingModule
import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.RegisteredBackfillQuery
import com.squareup.backfila.service.ServiceQuery
import com.squareup.protos.backfila.service.ConfigureServiceRequest
import com.squareup.protos.backfila.service.ServiceType
import misk.MiskCaller
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

@MiskTest(startService = true)
class ConfigureServiceActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaWebActionTestingModule()

  @Inject lateinit var clock: Clock
  @Inject lateinit var configureServiceAction: ConfigureServiceAction
  @Inject @BackfilaDb lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var scope: ActionScope

  @Test
  fun configureServiceSyncsBackfills() {
    val seedData: Map<Key<*>, Any> = mapOf(
        keyOf<MiskCaller>() to MiskCaller("franklin"))

    scope.enter(seedData).use {
      configureServiceAction.configureService(
          ConfigureServiceRequest(listOf(), ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).isEmpty()

      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null, false)),
          ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).containsOnly("xyz")
      assertThat(deletedBackfillNames("franklin")).isEmpty()

      configureServiceAction.configureService(
          ConfigureServiceRequest(
              listOf(ConfigureServiceRequest.BackfillData("zzz", listOf(), null, null, false)),
              ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).containsOnly("zzz")
      assertThat(deletedBackfillNames("franklin")).containsOnly("xyz")
    }
  }

  @Test
  fun deleteOneOfTwo() {
    val seedData: Map<Key<*>, Any> = mapOf(
        keyOf<MiskCaller>() to MiskCaller("franklin"))

    scope.enter(seedData).use {
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null, false),
          ConfigureServiceRequest.BackfillData("zzz", listOf(), null, null, false)),
          ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).containsOnly("xyz", "zzz")
      assertThat(deletedBackfillNames("franklin")).isEmpty()

      configureServiceAction.configureService(
          ConfigureServiceRequest(
              listOf(ConfigureServiceRequest.BackfillData("zzz", listOf(), null, null, false)),
              ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).containsOnly("zzz")
      assertThat(deletedBackfillNames("franklin")).containsOnly("xyz")
    }
  }

  @Test
  fun reintroduceBackfill() {
    val seedData: Map<Key<*>, Any> = mapOf(
        keyOf<MiskCaller>() to MiskCaller("franklin"))

    scope.enter(seedData).use {
      configureServiceAction.configureService(
          ConfigureServiceRequest(listOf(), ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).isEmpty()

      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null, false)),
          ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).containsOnly("xyz")
      assertThat(deletedBackfillNames("franklin")).isEmpty()

      configureServiceAction.configureService(
          ConfigureServiceRequest(
              listOf(),
              ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).isEmpty()
      assertThat(deletedBackfillNames("franklin")).containsOnly("xyz")

      configureServiceAction.configureService(
          ConfigureServiceRequest(
              listOf(ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null, false)),
              ServiceType.SQUARE_DC))
      // The reintroduced backfill is created, and the old one kept around.
      assertThat(backfillNames("franklin")).containsOnly("xyz")
      assertThat(deletedBackfillNames("franklin")).containsOnly("xyz")
    }
  }

  @Test
  fun modifyBackfillRequiresApprovalBackAndForth() {
    val seedData: Map<Key<*>, Any> = mapOf(
        keyOf<MiskCaller>() to MiskCaller("franklin"))

    scope.enter(seedData).use {
      // Going back and forth between approval required creates new registered backfills.
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null, false)),
          ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).containsOnly("xyz")
      assertThat(deletedBackfillNames("franklin")).isEmpty()

      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null, true)),
          ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).containsOnly("xyz")
      assertThat(deletedBackfillNames("franklin")).containsOnly("xyz")

      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null, false)),
          ServiceType.SQUARE_DC))
      val backfills = backfills("franklin")
      assertThat(backfills).containsOnly(
          Backfill("xyz", clock.instant(), type_provided = null, type_consumed = null,
              parameter_names = "", requires_approval = false),
          Backfill("xyz", clock.instant(), type_provided = null, type_consumed = null,
              parameter_names = "", requires_approval = true),
          Backfill("xyz", null, type_provided = null, type_consumed = null,
              parameter_names = "", requires_approval = false)
      )
    }
  }

  @Test
  fun modifyParams() {
    val seedData: Map<Key<*>, Any> = mapOf(
        keyOf<MiskCaller>() to MiskCaller("franklin"))

    scope.enter(seedData).use {
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null, false)),
          ServiceType.SQUARE_DC))
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf("abc"), null, null, false)),
          ServiceType.SQUARE_DC))
      val backfills = backfills("franklin")
      assertThat(backfills).containsOnly(
          Backfill("xyz", clock.instant(), type_provided = null, type_consumed = null,
              parameter_names = "", requires_approval = false),
          Backfill("xyz", null, type_provided = null, type_consumed = null,
              parameter_names = "abc", requires_approval = false)
      )
    }
  }

  @Test
  fun modifyTypeProvided() {
    val seedData: Map<Key<*>, Any> = mapOf(
        keyOf<MiskCaller>() to MiskCaller("franklin"))

    scope.enter(seedData).use {
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), "String", null, false)),
          ServiceType.SQUARE_DC))
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), "Int", null, false)),
          ServiceType.SQUARE_DC))
      val backfills = backfills("franklin")
      assertThat(backfills).containsOnly(
          Backfill("xyz", clock.instant(), type_provided = "String", type_consumed = null,
              parameter_names = "", requires_approval = false),
          Backfill("xyz", null, type_provided = "Int", type_consumed = null,
              parameter_names = "", requires_approval = false)
      )
    }
  }

  @Test
  fun modifyTypeConsumed() {
    val seedData: Map<Key<*>, Any> = mapOf(
        keyOf<MiskCaller>() to MiskCaller("franklin"))

    scope.enter(seedData).use {
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, "String", false)),
          ServiceType.SQUARE_DC))
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, "Int", false)),
          ServiceType.SQUARE_DC))
      val backfills = backfills("franklin")
      assertThat(backfills).containsOnly(
          Backfill("xyz", clock.instant(), type_provided = null, type_consumed = "String",
              parameter_names = "", requires_approval = false),
          Backfill("xyz", null, type_provided = null, type_consumed = "Int",
              parameter_names = "", requires_approval = false)
      )
    }
  }

  @Test
  fun unchangedBackfillNotDuplicated() {
    val seedData: Map<Key<*>, Any> = mapOf(
        keyOf<MiskCaller>() to MiskCaller("franklin"))

    scope.enter(seedData).use {
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, "String", false)),
          ServiceType.SQUARE_DC))
      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, "String", false)),
          ServiceType.SQUARE_DC))
      val backfills = backfills("franklin")
      assertThat(backfills).containsOnly(
          Backfill("xyz", deleted_in_service_at = null, type_provided = null,
              type_consumed = "String", parameter_names = "", requires_approval = false)
      )
    }
  }

  private fun backfillNames(serviceName: String): List<String> {
    return transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
          .registryName(serviceName)
          .uniqueResult(session)!!
      queryFactory.newQuery<RegisteredBackfillQuery>()
          .serviceId(dbService.id)
          .active()
          .list(session)
          .map { it.name }
    }
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
