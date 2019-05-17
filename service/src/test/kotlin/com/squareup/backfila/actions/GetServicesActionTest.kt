package com.squareup.backfila.actions

import com.google.inject.Module
import com.squareup.backfila.BackfilaTestingModule
import com.squareup.backfila.api.ConfigureServiceAction
import com.squareup.backfila.dashboard.GetServicesAction
import com.squareup.backfila.fakeCaller
import com.squareup.protos.backfila.service.ConfigureServiceRequest
import com.squareup.protos.backfila.service.ServiceType
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
  val module: Module = BackfilaTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction
  @Inject lateinit var getServicesAction: GetServicesAction
  @Inject lateinit var scope: ActionScope

  @Test
  fun noServices() {
    scope.fakeCaller(user = "molly") {
      assertThat(getServicesAction.services().services).isEmpty()
    }
  }

  @Test
  fun oneService() {
    scope.fakeCaller(service="deep-fryer") {
      configureServiceAction.configureService(
          ConfigureServiceRequest(listOf(), ServiceType.SQUARE_DC))
    }

    scope.fakeCaller(user="molly") {
      assertThat(getServicesAction.services().services).containsOnly("deep-fryer")
    }
  }

  @Test
  fun twoServices() {
    scope.fakeCaller(service="deep-fryer") {
      configureServiceAction.configureService(
          ConfigureServiceRequest(listOf(), ServiceType.SQUARE_DC))
    }
    scope.fakeCaller(service="freezer") {
      configureServiceAction.configureService(
          ConfigureServiceRequest(listOf(), ServiceType.SQUARE_DC))
    }
    scope.fakeCaller(user="molly") {
      assertThat(getServicesAction.services().services).containsOnly("deep-fryer", "permit")
    }
  }
}
