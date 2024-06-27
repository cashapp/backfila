package app.cash.backfila.development

import misk.MiskCaller
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.DevelopmentOnly
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.AdminDashboardModule
import misk.web.metadata.all.AllMetadataAccess
import misk.web.metadata.all.AllMetadataModule
import misk.web.metadata.config.ConfigMetadataAction

class DevelopmentAdminDashboardModule : KAbstractModule() {
  override fun configure() {
    install(AdminDashboardModule(isDevelopment = true, configTabMode = ConfigMetadataAction.ConfigTabMode.SHOW_REDACTED_EFFECTIVE_CONFIG))
    install(AllMetadataModule())
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AdminDashboardAccess>(
        capabilities = listOf("admin_console"),
      ),
    )
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AllMetadataAccess>(
        capabilities = listOf("admin_console"),
        services = listOf(),
      ),
    )
    // Setup authentication in the development environment
    bind<MiskCaller>().annotatedWith<DevelopmentOnly>()
      .toInstance(MiskCaller(user = "testfila", capabilities = setOf("admin_console", "users")))
  }
}
