package com.squareup.backfila.actions

import com.google.inject.Module
import com.google.inject.util.Modules
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class PingActionTest {

  @Suppress("unused")
  @MiskTestModule
  val module: Module = Modules.combine(BackfilaWebActionTestingModule())

  @Inject lateinit var pingAction: PingAction

  @Test
  fun ping() {

    val pong = pingAction.ping(PingAction.Ping("hi"))
    assertThat(pong.message).contains("hi")
  }
}
