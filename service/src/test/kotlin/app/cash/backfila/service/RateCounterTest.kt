package app.cash.backfila.service

import java.util.concurrent.TimeUnit
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class RateCounterTest {
  @Test
  fun test() {
    val clock = FakeClock()
    val rateCounter = RateCounter(clock)
    rateCounter.add(1)
    assertThat(rateCounter.sum()).isEqualTo(1)
    clock.add(1, TimeUnit.SECONDS)
    assertThat(rateCounter.sum()).isEqualTo(1)
    rateCounter.add(2)
    assertThat(rateCounter.sum()).isEqualTo(3)
    rateCounter.add(1)
    assertThat(rateCounter.sum()).isEqualTo(4)
    clock.add(5, TimeUnit.SECONDS)
    rateCounter.add(3)
    clock.add(56, TimeUnit.SECONDS)
    assertThat(rateCounter.sum()).isEqualTo(3)
    clock.add(4, TimeUnit.SECONDS)
    assertThat(rateCounter.sum()).isEqualTo(3)
    clock.add(1, TimeUnit.SECONDS)
    assertThat(rateCounter.sum()).isEqualTo(0)
  }

  @Test
  fun projectedRateDropsOff() {
    val clock = FakeClock()
    val rateCounter = RateCounter(clock)
    assertThat(rateCounter.projectedRate()).isEqualTo(0)

    rateCounter.add(1)
    assertThat(rateCounter.projectedRate()).isEqualTo(60)

    clock.add(1, TimeUnit.SECONDS)
    assertThat(rateCounter.projectedRate()).isEqualTo(60)

    clock.add(1, TimeUnit.SECONDS)
    assertThat(rateCounter.projectedRate()).isEqualTo(30)

    clock.add(28, TimeUnit.SECONDS)
    assertThat(rateCounter.projectedRate()).isEqualTo(2)

    clock.add(15, TimeUnit.SECONDS)
    assertThat(rateCounter.projectedRate()).isEqualTo(1)

    clock.add(14, TimeUnit.SECONDS)
    assertThat(rateCounter.projectedRate()).isEqualTo(1)

    clock.add(1, TimeUnit.SECONDS)
    assertThat(rateCounter.projectedRate()).isEqualTo(1)

    clock.add(1, TimeUnit.SECONDS)
    assertThat(rateCounter.projectedRate()).isEqualTo(0)
  }

  @Test
  fun projectedRateStays() {
    val clock = FakeClock()
    val rateCounter = RateCounter(clock)
    clock.add(1, TimeUnit.SECONDS)
    rateCounter.add(10)
    assertThat(rateCounter.projectedRate()).isEqualTo(600)

    clock.add(1, TimeUnit.SECONDS)
    rateCounter.add(10)
    assertThat(rateCounter.projectedRate()).isEqualTo(600)

    clock.add(1, TimeUnit.SECONDS)
    rateCounter.add(10)
    assertThat(rateCounter.projectedRate()).isEqualTo(600)
  }
}
