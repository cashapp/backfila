package app.cash.backfila.client.fixedset

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sample datastore for testing Backfila client.
 *
 * Not safe for concurrent use.
 */
@Singleton
class FixedSetDatastore @Inject constructor() {
  val dataByInstance = mutableMapOf<String, MutableList<FixedSetRow>>()
  var nextId = 0L

  fun put(instance: String, vararg values: String) {
    val rows = dataByInstance.getOrPut(instance) { mutableListOf() }
    for (value in values) {
      rows += FixedSetRow(nextId++, value)
    }
  }

  fun valuesToList(): List<String> = dataByInstance.values
    .flatMap { it }
    .sortedBy { it.id }
    .map { it.value }
}
