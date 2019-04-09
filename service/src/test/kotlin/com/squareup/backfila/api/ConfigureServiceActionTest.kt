package com.squareup.backfila.api

import com.google.inject.Key
import com.google.inject.Module
import com.squareup.backfila.actions.BackfilaWebActionTestingModule
import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.RegisteredBackfillQuery
import com.squareup.backfila.service.ServiceQuery
import com.squareup.protos.cash.backfila.service.ConfigureServiceRequest
import com.squareup.protos.cash.backfila.service.ServiceType
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
import javax.inject.Inject

@MiskTest(startService = true)
class ConfigureServiceActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaWebActionTestingModule()

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
      assertThat(backfillNames("franklin")).containsOnly()

      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null)),
          ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).containsOnly("xyz")
      assertThat(deletedBackfillNames("franklin")).containsOnly()

      configureServiceAction.configureService(
          ConfigureServiceRequest(
              listOf(ConfigureServiceRequest.BackfillData("zzz", listOf(), null, null)),
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
      assertThat(backfillNames("franklin")).containsOnly()

      configureServiceAction.configureService(ConfigureServiceRequest(listOf(
          ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null)),
          ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).containsOnly("xyz")
      assertThat(deletedBackfillNames("franklin")).containsOnly()

      configureServiceAction.configureService(
          ConfigureServiceRequest(
              listOf(),
              ServiceType.SQUARE_DC))
      assertThat(backfillNames("franklin")).containsOnly()
      assertThat(deletedBackfillNames("franklin")).containsOnly("xyz")

      configureServiceAction.configureService(
          ConfigureServiceRequest(
              listOf(ConfigureServiceRequest.BackfillData("xyz", listOf(), null, null)),
              ServiceType.SQUARE_DC))
      // The reintroduced backfill is created, and the old one kept around.
      assertThat(backfillNames("franklin")).containsOnly("xyz")
      assertThat(deletedBackfillNames("franklin")).containsOnly("xyz")
    }
  }

  private fun backfillNames(serviceName: String): List<String> {
    return transacter.transaction { session ->
      var dbService = queryFactory.newQuery<ServiceQuery>()
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
      var dbService = queryFactory.newQuery<ServiceQuery>()
          .registryName(serviceName)
          .uniqueResult(session)!!
      queryFactory.newQuery<RegisteredBackfillQuery>()
          .serviceId(dbService.id)
          .notActive()
          .list(session)
          .map { it.name }
    }
  }
}
