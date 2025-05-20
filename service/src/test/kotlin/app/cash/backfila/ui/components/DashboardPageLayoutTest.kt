package app.cash.backfila.ui.components

import app.cash.backfila.BackfilaTestingModule
import com.google.inject.Provider
import jakarta.inject.Inject
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
import misk.web.v2.DashboardPageLayout
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test
import javax.servlet.http.HttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MiskTest
class DashboardPageLayoutTest {
  @MiskTestModule
  private val module = TestModule()

  @Inject
  lateinit var actionScope: ActionScope

  @Inject
  lateinit var layout: Provider<DashboardPageLayout>

  private val fakeHttpCall = FakeHttpCall(url = "http://foobar.com/abc/123".toHttpUrl())
  private val fakeMiskCaller = MiskCaller(user = "test-user")

  @Test
  fun `happy path`() {
    actionScope.enter(
      mapOf(
        HttpCall::class.toKey() to fakeHttpCall,
        MiskCaller::class.toKey() to fakeMiskCaller
      )
    ).use {
      // No exception thrown on correct usage
      layout.get().newBuilder().build()
    }
  }

  @Test
  fun `no builder reuse permitted`() {
    actionScope.enter(
      mapOf(
        HttpCall::class.toKey() to fakeHttpCall,
        MiskCaller::class.toKey() to MiskCaller(user = "test-user"),
      ),
    ).use {
      // Fresh builder must have newBuilder() called
      val e1 = assertFailsWith<IllegalStateException> { layout.get().build() }
      assertEquals(
        "You must call newBuilder() before calling build() to prevent builder reuse.", e1.message,
      )

      // No builder reuse
      val e2 = assertFailsWith<IllegalStateException> {
        val newBuilder = layout.get().newBuilder()
        newBuilder.build()
        // Not allowed to call build() twice on same builder
        newBuilder.build()
      }
      assertEquals(
        "You must call newBuilder() before calling build() to prevent builder reuse.", e2.message,
      )
    }
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
