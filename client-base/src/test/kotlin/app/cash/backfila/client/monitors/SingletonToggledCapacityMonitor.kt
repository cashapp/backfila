package app.cash.backfila.client.monitors

import app.cash.backfila.client.monitor.CapacityMonitor
import javax.inject.Singleton

@Singleton
class SingletonToggledCapacityMonitor : ToggledCapacityMonitor() {
}