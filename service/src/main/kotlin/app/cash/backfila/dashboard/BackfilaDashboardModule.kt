package app.cash.backfila.dashboard

import app.cash.backfila.ui.UiModule
import javax.inject.Qualifier
import misk.inject.KAbstractModule
import misk.security.authz.Unauthenticated
import misk.web.WebActionModule
import misk.web.dashboard.DashboardModule
import wisp.deployment.Deployment

class BackfilaDashboardModule(val deployment: Deployment) : KAbstractModule() {
  override fun configure() {
    // Old UI at /app/
    install(WebActionModule.create<AppRedirectAction>())
    install(
      DashboardModule.createMiskWebDashboard<BackfilaApp, Unauthenticated>(
        isDevelopment = deployment.isLocalDevelopment,
        slug = "app",
        developmentWebProxyUrl = "http://localhost:4200/",
        urlPathPrefix = "/app/",
        resourcePathPrefix = "/app/",
        menuLabel = "App",
      ),
    )

    // New UI at /
    install(UiModule())
  }
}

/** Dashboard Annotation used for all tabs bound in the Backfila App */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class BackfilaApp
