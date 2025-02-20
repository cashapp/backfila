package app.cash.backfila.service

import app.cash.backfila.service.runner.statemachine.VariableCapacityChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class VariableCapacityChannelTest {
  @Test
  fun empty() = runTest {
    val variableCapacityChannel = VariableCapacityChannel<String>(1)
    val upstream = variableCapacityChannel.upstream()
    launch {
      val receiveChannel = variableCapacityChannel.proxy(this)
      assertThat(receiveChannel.tryReceive().getOrNull()).isNull()
      upstream.close()
    }
  }

  @Test
  fun sendBlockedUntilCoroutineRunsAndBuffers() = runTest {
    val variableCapacityChannel = VariableCapacityChannel<String>(1)
    val upstream = variableCapacityChannel.upstream()
    assertThat(upstream.trySend("test").isSuccess).isFalse()
    assertThat(variableCapacityChannel.queued()).isEqualTo(0)
    launch {
      val receiveChannel = variableCapacityChannel.proxy(this)
      upstream.send("test")
      assertThat(variableCapacityChannel.queued()).isEqualTo(1)
      assertThat(upstream.trySend("test2").isSuccess).isFalse()
      assertThat(receiveChannel.receive()).isEqualTo("test")
      upstream.close()
    }
  }

  @Test
  fun receiveUnblocksSend() = runTest {
    val variableCapacityChannel = VariableCapacityChannel<String>(1)
    val upstream = variableCapacityChannel.upstream()
    launch {
      val receiveChannel = variableCapacityChannel.proxy(this)
      upstream.send("test")
      assertThat(variableCapacityChannel.queued()).isEqualTo(1)
      assertThat(upstream.trySend("test2").isSuccess).isFalse()
      assertThat(receiveChannel.receive()).isEqualTo("test")
      upstream.send("test2")
      assertThat(variableCapacityChannel.queued()).isEqualTo(1)
      assertThat(receiveChannel.receive()).isEqualTo("test2")
      upstream.close()
    }
  }

  @Test
  fun increaseCapacityUnblocksAfterRead() = runTest {
    val variableCapacityChannel = VariableCapacityChannel<String>(1)
    val upstream = variableCapacityChannel.upstream()
    launch {
      val receiveChannel = variableCapacityChannel.proxy(this)
      upstream.send("test")
      assertThat(variableCapacityChannel.queued()).isEqualTo(1)
      assertThat(upstream.trySend("test2").isSuccess).isFalse()
      variableCapacityChannel.capacity = 2
      // Capacity change only takes affect when it is not blocked on sending
      assertThat(receiveChannel.receive()).isEqualTo("test")

      upstream.send("test2")
      assertThat(variableCapacityChannel.queued()).isEqualTo(1)
      upstream.send("test3")

      assertThat(receiveChannel.receive()).isEqualTo("test2")
      assertThat(receiveChannel.receive()).isEqualTo("test3")

      upstream.close()
    }
  }

  @Test
  fun decreaseCapacity() = runTest {
    val variableCapacityChannel = VariableCapacityChannel<String>(2)
    val upstream = variableCapacityChannel.upstream()
    launch {
      val receiveChannel = variableCapacityChannel.proxy(this)
      upstream.send("test")
      assertThat(variableCapacityChannel.queued()).isEqualTo(1)
      upstream.send("test2")
      assertThat(upstream.trySend("test3").isSuccess).isFalse()

      variableCapacityChannel.capacity = 1
      // Capacity change only takes affect when it is not blocked on sending
      assertThat(receiveChannel.receive()).isEqualTo("test")
      assertThat(variableCapacityChannel.queued()).isEqualTo(1)
      // Still can't send after receiving because capacity was lowered.
      assertThat(upstream.trySend("test3").isSuccess).isFalse()

      assertThat(receiveChannel.receive()).isEqualTo("test2")
      upstream.send("test3")

      assertThat(receiveChannel.receive()).isEqualTo("test3")

      upstream.close()
    }
  }

  @Test
  fun closeUpstream() = runTest {
    val variableCapacityChannel = VariableCapacityChannel<String>(1)
    val upstream = variableCapacityChannel.upstream()
    launch {
      val receiveChannel = variableCapacityChannel.proxy(this)
      upstream.send("test")
      upstream.close()

      assertThat(receiveChannel.receive()).isEqualTo("test")
      try {
        receiveChannel.receive()
        fail("channel not closed")
      } catch (e: ClosedReceiveChannelException) {
      }
    }
  }

  @Test
  fun cancelUpstream() = runTest {
    val variableCapacityChannel = VariableCapacityChannel<String>(1)
    val upstream = variableCapacityChannel.upstream()
    launch {
      val receiveChannel = variableCapacityChannel.proxy(this)
      upstream.send("test")
      upstream.close(CancellationException("cancel"))

      assertThat(receiveChannel.receive()).isEqualTo("test")
      try {
        receiveChannel.receive()
        fail("channel not canceled")
      } catch (e: CancellationException) {
      }
    }
  }

  @Test
  fun closeDownstream() = runTest {
    val variableCapacityChannel = VariableCapacityChannel<String>(1)
    val upstream = variableCapacityChannel.upstream()
    launch {
      val receiveChannel = variableCapacityChannel.proxy(this)
      upstream.send("test")
      receiveChannel.cancel()

      try {
        upstream.send("test2")
        fail("channel not closed")
      } catch (e: ClosedSendChannelException) {
      }
    }
  }

  @Test
  fun cancelDownstream() = runTest {
    val variableCapacityChannel = VariableCapacityChannel<String>(1)
    val upstream = variableCapacityChannel.upstream()
    launch {
      val receiveChannel = variableCapacityChannel.proxy(this)
      upstream.send("test")
      receiveChannel.cancel(CancellationException("cancel"))

      try {
        upstream.send("test2")
        fail("channel not canceled")
      } catch (e: CancellationException) {
      }
    }
  }

  @Test
  fun `listener is called`() {
    val size = AtomicInteger()
    runTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(
        capacity = 3,
        queueSizeChangeListener = size::set,
      )
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(size.get()).isEqualTo(0)

        upstream.send("test")
        upstream.send("test")
        upstream.send("test")

        receiveChannel.receive()
        receiveChannel.receive()
        receiveChannel.receive()

        upstream.close()
      }
    }
  }
}
