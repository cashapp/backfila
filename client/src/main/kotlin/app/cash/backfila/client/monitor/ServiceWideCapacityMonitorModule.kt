package app.cash.backfila.client.monitor

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import javax.inject.Qualifier
import kotlin.reflect.KClass

class ServiceWideCapacityMonitorModule<T : CapacityMonitor> private constructor(
  private val capacityMonitorClass: KClass<T>,
) : AbstractModule() {
  override fun configure() {
    // Ensures that the backfill class is injectable. If you are failing this check you probably
    // want to add an @Inject annotation to your class or check that all of your dependencies are provided.
    binder().getProvider(capacityMonitorClass.java)
    Multibinder.newSetBinder(binder(), CapacityMonitor::class.java, ServiceWide::class.java)
      .addBinding()
      .to(capacityMonitorClass.java)
  }

  companion object {
    inline fun <reified T : CapacityMonitor> create(): ServiceWideCapacityMonitorModule<T> =
      create(T::class)

    @JvmStatic
    fun <T : CapacityMonitor> create(capacityMonitorClass: KClass<T>): ServiceWideCapacityMonitorModule<T> {
      return ServiceWideCapacityMonitorModule(capacityMonitorClass)
    }

    @JvmStatic
    fun <T : CapacityMonitor> create(capacityMonitorClass: Class<T>): ServiceWideCapacityMonitorModule<T> {
      return ServiceWideCapacityMonitorModule(capacityMonitorClass.kotlin)
    }
  }
}

/** Annotation for specifying service wide capacity monitors. */
@Qualifier annotation class ServiceWide
