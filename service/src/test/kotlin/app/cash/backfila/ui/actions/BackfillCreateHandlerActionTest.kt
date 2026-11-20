package app.cash.backfila.ui.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import jakarta.inject.Inject
import javax.servlet.http.HttpServletRequest
import misk.Action
import misk.MiskCaller
import misk.api.HttpRequest
import misk.inject.KAbstractModule
import misk.inject.toKey
import misk.scope.ActionScope
import misk.scope.ActionScopedProviderModule
import misk.security.authz.MiskCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.FakeHttpCall
import misk.web.HttpCall
import misk.web.dashboard.BaseDashboardModule
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@MiskTest(startService = true)
class BackfillCreateHandlerActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction

  @Inject lateinit var backfillCreateHandlerAction: BackfillCreateHandlerAction

  @Inject lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject lateinit var scope: ActionScope

  @BeforeEach
  fun setup() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(listOf(ConfigureServiceRequest.BackfillData.Builder().name("ChickenSandwich").build()))
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
  }

  @ParameterizedTest(name = "dryRun={0} creates dry_run={1}")
  @CsvSource("on, true", "true, true", "'', true", "off, false", "false, false", "FALSE, false")
  fun `dryRun value sets dry run`(value: String, expected: Boolean) {
    assertThat(submit("&dryRun=$value")).isEqualTo(expected)
  }

  @Test
  fun `missing dryRun is not a dry run`() {
    assertThat(submit("")).isFalse()
  }

  private fun submit(extraQuery: String): Boolean {
    val url = (
      "http://backfila.test${BackfillCreateHandlerAction.PATH}" +
        "?service=deep-fryer&variant=default&backfillName=ChickenSandwich$extraQuery"
      ).toHttpUrl()
    val response = scope.create(
      mapOf(
        HttpCall::class.toKey() to FakeHttpCall(url = url),
        MiskCaller::class.toKey() to MiskCaller(user = "molly"),
      ),
    ).inScope { backfillCreateHandlerAction.get() }

    assertThat(response.statusCode).isEqualTo(303)
    val backfillId = response.headers["Location"]!!.substringAfterLast("/").toLong()
    return getBackfillStatusAction.status(backfillId).dry_run
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(BackfilaTestingModule())
      install(BaseDashboardModule(true))
      install(
        object : ActionScopedProviderModule() {
          override fun configureProviders() {
            bindSeedData(HttpCall::class)
            bindSeedData(HttpRequest::class)
            bindSeedData(HttpServletRequest::class)
            bindSeedData(Action::class)
            newMultibinder<MiskCallerAuthenticator>()
          }
        },
      )
    }
  }
}
