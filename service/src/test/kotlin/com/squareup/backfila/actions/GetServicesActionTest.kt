package com.squareup.backfila.actions

import com.google.inject.Module
import com.squareup.backfila.api.ConfigureServiceAction
import com.squareup.backfila.dashboard.GetServicesAction
import com.squareup.protos.cash.backfila.service.ConfigureServiceRequest
import com.squareup.protos.cash.backfila.service.ServiceType
import misk.MiskCaller
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class GetServicesActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaWebActionTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction
  @Inject lateinit var getServicesAction: GetServicesAction
  @Inject lateinit var scope: ActionScope

  @Test
  fun noServices() {
    scope(MiskCaller(user = "bob")).use {
      assertThat(getServicesAction.services().services).isEmpty()
    }
  }

  @Test
  fun oneService() {
    scope(MiskCaller(service="franklin")).use {
      configureServiceAction.configureService(
          ConfigureServiceRequest(listOf(), ServiceType.SQUARE_DC))
    }

    scope(MiskCaller(user="bob")).use {
      assertThat(getServicesAction.services().services).containsOnly("franklin")
    }
  }

  @Test
  fun twoServices() {
    scope(MiskCaller(service="franklin")).use {
      configureServiceAction.configureService(
          ConfigureServiceRequest(listOf(), ServiceType.SQUARE_DC))
    }
    scope(MiskCaller(service="permit")).use {
      configureServiceAction.configureService(
          ConfigureServiceRequest(listOf(), ServiceType.SQUARE_DC))
    }
    scope(MiskCaller(user="bob")).use {
      assertThat(getServicesAction.services().services).containsOnly("franklin", "permit")
    }
  }

  fun scope(caller: MiskCaller) =
      scope.enter(mapOf(keyOf<MiskCaller>() to caller))
}
