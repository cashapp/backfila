package app.cash.backfila.service

import app.cash.backfila.service.runner.statemachine.VariableCapacityChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class VariableCapacityChannelTest {
  @Test
  fun empty() {
    runTest(UnconfinedTestDispatcher()) {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(receiveChannel.tryReceive().getOrNull()).isNull()
        upstream.close()
      }
    }
  }

  @Test
  fun sendBlockedUntilCoroutineRunsAndBuffers() {
    runTest(UnconfinedTestDispatcher()) {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      assertThat(upstream.trySend("test").isSuccess).isFalse()
      assertThat(variableCapacityChannel.queued()).isEqualTo(0)
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.trySend("test").isSuccess).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(upstream.trySend("test2").isSuccess).isFalse()
        assertThat(receiveChannel.tryReceive().getOrNull()).isEqualTo("test")
        upstream.close()
      }
    }
  }

  @Test
  fun receiveUnblocksSend() {
    runTest(UnconfinedTestDispatcher()) {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.trySend("test").isSuccess).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(upstream.trySend("test2").isSuccess).isFalse()
        assertThat(receiveChannel.tryReceive().getOrNull()).isEqualTo("test")
        assertThat(variableCapacityChannel.queued()).isEqualTo(0)
        assertThat(upstream.trySend("test2").isSuccess).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(receiveChannel.tryReceive().getOrNull()).isEqualTo("test2")
        assertThat(variableCapacityChannel.queued()).isEqualTo(0)
        upstream.close()
      }
    }
  }

  @Test
  fun increaseCapacityUnblocksAfterRead() {
    runTest(UnconfinedTestDispatcher()) {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.trySend("test").isSuccess).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(upstream.trySend("test2").isSuccess).isFalse()
        variableCapacityChannel.capacity = 2
        // Capacity change only takes affect when it is not blocked on sending
        assertThat(receiveChannel.tryReceive().getOrNull()).isEqualTo("test")

        assertThat(upstream.trySend("test2").isSuccess).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(upstream.trySend("test3").isSuccess).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(2)

        assertThat(receiveChannel.tryReceive().getOrNull()).isEqualTo("test2")
        assertThat(receiveChannel.tryReceive().getOrNull()).isEqualTo("test3")

        upstream.close()
      }
    }
  }

  @Test
  fun decreaseCapacity() {
    runTest(UnconfinedTestDispatcher()) {
      val variableCapacityChannel = VariableCapacityChannel<String>(2)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.trySend("test").isSuccess).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(upstream.trySend("test2").isSuccess).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(2)
        assertThat(upstream.trySend("test3").isSuccess).isFalse()

        variableCapacityChannel.capacity = 1
        // Capacity change only takes affect when it is not blocked on sending
        assertThat(receiveChannel.tryReceive().getOrNull()).isEqualTo("test")
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        // Still can't send after receiving because capacity was lowered.
        assertThat(upstream.trySend("test3").isSuccess).isFalse()

        assertThat(receiveChannel.tryReceive().getOrNull()).isEqualTo("test2")
        assertThat(variableCapacityChannel.queued()).isEqualTo(0)
        assertThat(upstream.trySend("test3").isSuccess).isTrue()

        assertThat(receiveChannel.tryReceive().getOrNull()).isEqualTo("test3")

        upstream.close()
      }
    }
  }

  @Test fun closeUpstream() {
    runTest(UnconfinedTestDispatcher()) {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.trySend("test").isSuccess).isTrue()
        upstream.close()

        assertThat(receiveChannel.receive()).isEqualTo("test")
        try {
          receiveChannel.receive()
          fail("channel not closed")
        } catch (e: ClosedReceiveChannelException) {
        }
      }
    }
  }

  @Test
  fun cancelUpstream() {
    runTest(UnconfinedTestDispatcher()) {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.trySend("test").isSuccess).isTrue()
        upstream.close(CancellationException("cancel"))

        assertThat(receiveChannel.receive()).isEqualTo("test")
        try {
          receiveChannel.receive()
          fail("channel not canceled")
        } catch (e: CancellationException) {
        }
      }
    }
  }

  @Test
  fun closeDownstream() {
    runTest(UnconfinedTestDispatcher()) {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.trySend("test").isSuccess).isTrue()
        receiveChannel.cancel()

        try {
          upstream.send("test2")
          fail("channel not closed")
        } catch (e: ClosedSendChannelException) {
        }
      }
    }
  }

  @Test
  fun cancelDownstream() {
    runTest(UnconfinedTestDispatcher()) {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.trySend("test").isSuccess).isTrue()
        receiveChannel.cancel(CancellationException("cancel"))

        try {
          upstream.send("test2")
          fail("channel not canceled")
        } catch (e: CancellationException) {
        }
      }
    }
  }

  @Test
  fun `listener is called`() {
    val size = AtomicInteger()
    runTest(UnconfinedTestDispatcher()) {
      val variableCapacityChannel = VariableCapacityChannel<String>(
        capacity = 3,
        queueSizeChangeListener = size::set,
      )
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(size.get()).isEqualTo(0)

        upstream.trySend("test").isSuccess
        assertThat(size.get()).isEqualTo(1)

        upstream.trySend("test").isSuccess
        assertThat(size.get()).isEqualTo(2)

        upstream.trySend("test").isSuccess
        assertThat(size.get()).isEqualTo(3)

        receiveChannel.tryReceive().getOrNull()
        assertThat(size.get()).isEqualTo(2)

        receiveChannel.tryReceive().getOrNull()
        assertThat(size.get()).isEqualTo(1)

        receiveChannel.tryReceive().getOrNull()
        assertThat(size.get()).isEqualTo(0)

        upstream.close()
      }
    }
  }
}
