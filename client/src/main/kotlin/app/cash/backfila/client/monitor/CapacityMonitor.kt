package app.cash.backfila.client.monitor

interface CapacityMonitor {
  fun hasCapacity(): Boolean
}