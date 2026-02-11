package app.cash.backfila.client.monitors

import app.cash.backfila.client.monitor.CapacityMonitor
import javax.inject.Singleton

open class ToggledCapacityMonitor : CapacityMonitor {

  var hasCapacity: Boolean = true

  override fun hasCapacity() = hasCapacity

  fun capacityOn() {
    hasCapacity = true
  }

  fun capacityOff() {
    hasCapacity = false
  }
}