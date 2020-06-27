package app.cash.backfila.service.runner

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.max

class Backoff(val clock: Clock) {
  private var backoffUntil: Instant? = null

  fun backingOff() = backoffUntil?.isAfter(clock.instant()) ?: false
  fun backoffMs() = max(0, Duration.between(clock.instant(), backoffUntil!!).toMillis())

  fun addMillis(millis: Long) {
    backoffUntil = clock.instant().plusMillis(millis)
  }
}
