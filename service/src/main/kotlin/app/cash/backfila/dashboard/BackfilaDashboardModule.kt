package app.cash.backfila.dashboard

import javax.inject.Qualifier
import misk.inject.KAbstractModule
import misk.security.authz.Unauthenticated
import misk.web.dashboard.DashboardModule
import wisp.deployment.Deployment

class BackfilaDashboardModule(val deployment: Deployment) : KAbstractModule() {
  override fun configure() {
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
  }
}

/** Dashboard Annotation used for all tabs bound in the Backfila App */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class BackfilaApp
