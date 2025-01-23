package app.cash.backfila.ui.actions

import app.cash.backfila.dashboard.GetServicesAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceDataHelper @Inject constructor(
  private val getServicesAction: GetServicesAction,
) {
  fun getFlattenedServices(): Map<String, GetServicesAction.UiService> {
    val services = getServicesAction.services().services
    return services.flatMap { service ->
      service.variants.map { variant -> "${service.name}/$variant" to service }
    }.toMap()
  }
}
