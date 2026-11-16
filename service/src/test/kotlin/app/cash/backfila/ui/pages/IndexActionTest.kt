package app.cash.backfila.ui.pages

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import com.google.inject.Provides
import jakarta.inject.Inject
import misk.MiskCaller
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.scope.ActionScope
import misk.scope.ActionScopedProviderModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.FakeHttpCall
import misk.web.HttpCall
import misk.web.dashboard.DashboardTab
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class IndexActionTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var indexAction: IndexAction

  @Inject lateinit var configureServiceAction: ConfigureServiceAction

  @Inject lateinit var createBackfillAction: CreateBackfillAction

  @Inject lateinit var scope: ActionScope

  @Test
  fun `homepage shows backfills for the authenticated user`() {
    scope.fakeCaller(service = "bakery") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(listOf(ConfigureServiceRequest.BackfillData.Builder().name("Croissant").build()))
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
    fun create(user: String): Long = scope.fakeCaller(user = user) {
      createBackfillAction.create(
        "bakery", "default", CreateBackfillRequest.Builder().backfill_name("Croissant").build(),
      ).backfill_run_id
    }
    val ownId = create("molly")
    val otherId = create("molly.chen")

    val html = renderHome("molly")
    assertThat(html).contains("molly</span>!", "Your recent backfills", "Croissant", "href=\"/backfills/$ownId\"")
    assertThat(html).doesNotContain("href=\"/backfills/$otherId\"", "No recent backfills to show.", "Show deleted")
  }

  @Test
  fun `homepage shows an empty state for a user with no backfills`() {
    val html = renderHome("new-user")
    assertThat(html).contains("new-user</span>!", "Your recent backfills", "No recent backfills to show.")
    assertThat(html).doesNotContain("<table")
  }

  private fun renderHome(user: String): String = scope.create(
    mapOf(
      MiskCaller::class.toKey() to MiskCaller(user = user),
      HttpCall::class.toKey() to FakeHttpCall(url = "http://localhost/".toHttpUrl()),
    ),
  ).inScope { indexAction.get(null) }

  class TestModule : KAbstractModule() {
    @Provides
    fun dashboardTabs(): List<DashboardTab> = emptyList()

    override fun configure() {
      install(BackfilaTestingModule())
      install(object : ActionScopedProviderModule() {
        override fun configureProviders() {
          bindSeedData(HttpCall::class)
        }
      },
      )
    }
  }
}
