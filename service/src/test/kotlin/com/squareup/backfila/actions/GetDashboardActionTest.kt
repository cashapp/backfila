package com.squareup.backfila.actions

import com.google.inject.Module
import com.squareup.backfila.dashboard.GetDashboardAction
import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.DbService
import com.squareup.protos.cash.backfila.service.ServiceType
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class GetDashboardActionTest {

  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaWebActionTestingModule()

  @Inject lateinit var getDashboardAction: GetDashboardAction
  @Inject @BackfilaDb lateinit var transacter: Transacter

  @Test
  fun getDashboard() {
    val dashboard = getDashboardAction.loadDashboard()
    val x = transacter.transaction { session ->
      session.save(DbService("franklin", ServiceType.SQUARE_DC))
    }
    transacter.transaction { session ->
      val service = session.load(x)
      println("${service.registry_name} ${service.service_type}")
    }
//    assertThat(dashboard.backfills[0].name).isEqualTo("BackfillRavenTemplates")
  }
}
