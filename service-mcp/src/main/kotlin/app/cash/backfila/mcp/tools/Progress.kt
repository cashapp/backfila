package app.cash.backfila.mcp.tools

import kotlin.math.roundToLong

/** Returns [backfilled] as a percentage of [computed], to one decimal place, or null if nothing is computed yet. */
internal fun percentComplete(backfilled: Long, computed: Long): Double? {
  if (computed <= 0) return null
  return (backfilled.coerceAtMost(computed) * 1000.0 / computed).roundToLong() / 10.0
}
