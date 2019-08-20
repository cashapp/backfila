package com.squareup.backfila.service

import java.time.Clock
import java.util.LinkedList
import kotlin.math.max

/**
 * Calculates a sum of counts for a time period, defaulting to the last minute.
 */
class RateCounter(
  private val clock: Clock,
  private val lookbackSeconds: Long = 60L
) {
  data class Entry(val time: Long, var count: Long)

  private val deque = LinkedList<Entry>()

  private var sum: Long = 0

  private var startedAt: Long? = seconds()

  fun add(count: Long) {
    val seconds = seconds()
    if (deque.isNotEmpty() && deque.last.time == seconds) {
      deque.last.count += count
    } else {
      deque.add(Entry(seconds, count))
    }
    sum += count
  }

  fun sum(): Long {
    val seconds = seconds()
    while (deque.isNotEmpty()) {
      if (seconds - deque.first.time > lookbackSeconds) {
        sum -= deque.removeFirst().count
      } else {
        break
      }
    }
    return sum
  }

  /**
   * Get the sum, or if lookbackSeconds has not passed yet, a projected rate per lookbackSeconds
   * based on time passed.
   */
  fun projectedRate(): Long {
    val startedAt = startedAt
        ?: return sum()

    val seconds = seconds()
    val delta = seconds - startedAt
    if (delta >= lookbackSeconds) {
      // Null this out to skip computing time passed for all future calls.
      this.startedAt = null
      return sum()
    } else {
      val multiple = lookbackSeconds / max(delta, 1)
      return sum() * multiple
    }
  }

  private fun seconds() = clock.instant().epochSecond
}
