package app.cash.backfila.service

import app.cash.backfila.service.runner.statemachine.VariableCapacityChannel
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class VariableCapacityChannelTest {
  @Test
  fun empty() {
    runBlockingTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(receiveChannel.poll()).isNull()
        upstream.close()
      }
    }
  }

  @Test
  fun sendBlockedUntilCoroutineRunsAndBuffers() {
    runBlockingTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      assertThat(upstream.offer("test")).isFalse()
      assertThat(variableCapacityChannel.queued()).isEqualTo(0)
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.offer("test")).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(upstream.offer("test2")).isFalse()
        assertThat(receiveChannel.poll()).isEqualTo("test")
        upstream.close()
      }
    }
  }

  @Test
  fun receiveUnblocksSend() {
    runBlockingTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.offer("test")).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(upstream.offer("test2")).isFalse()
        assertThat(receiveChannel.poll()).isEqualTo("test")
        assertThat(variableCapacityChannel.queued()).isEqualTo(0)
        assertThat(upstream.offer("test2")).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(receiveChannel.poll()).isEqualTo("test2")
        assertThat(variableCapacityChannel.queued()).isEqualTo(0)
        upstream.close()
      }
    }
  }

  @Test
  fun increaseCapacityUnblocksAfterRead() {
    runBlockingTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.offer("test")).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(upstream.offer("test2")).isFalse()
        variableCapacityChannel.capacity = 2
        // Capacity change only takes affect when it is not blocked on sending
        assertThat(receiveChannel.poll()).isEqualTo("test")

        assertThat(upstream.offer("test2")).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(upstream.offer("test3")).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(2)

        assertThat(receiveChannel.poll()).isEqualTo("test2")
        assertThat(receiveChannel.poll()).isEqualTo("test3")

        upstream.close()
      }
    }
  }

  @Test
  fun decreaseCapacity() {
    runBlockingTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(2)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.offer("test")).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        assertThat(upstream.offer("test2")).isTrue()
        assertThat(variableCapacityChannel.queued()).isEqualTo(2)
        assertThat(upstream.offer("test3")).isFalse()

        variableCapacityChannel.capacity = 1
        // Capacity change only takes affect when it is not blocked on sending
        assertThat(receiveChannel.poll()).isEqualTo("test")
        assertThat(variableCapacityChannel.queued()).isEqualTo(1)
        // Still can't send after receiving because capacity was lowered.
        assertThat(upstream.offer("test3")).isFalse()

        assertThat(receiveChannel.poll()).isEqualTo("test2")
        assertThat(variableCapacityChannel.queued()).isEqualTo(0)
        assertThat(upstream.offer("test3")).isTrue()

        assertThat(receiveChannel.poll()).isEqualTo("test3")

        upstream.close()
      }
    }
  }

  @Test fun closeUpstream() {
    runBlockingTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.offer("test")).isTrue()
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
    runBlockingTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.offer("test")).isTrue()
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
    runBlockingTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.offer("test")).isTrue()
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
    runBlockingTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(1)
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(upstream.offer("test")).isTrue()
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
    runBlockingTest {
      val variableCapacityChannel = VariableCapacityChannel<String>(
        capacity = 3,
        queueSizeChangeListener = size::set,
      )
      val upstream = variableCapacityChannel.upstream()
      launch {
        val receiveChannel = variableCapacityChannel.proxy(this)
        assertThat(size.get()).isEqualTo(0)

        upstream.offer("test")
        assertThat(size.get()).isEqualTo(1)

        upstream.offer("test")
        assertThat(size.get()).isEqualTo(2)

        upstream.offer("test")
        assertThat(size.get()).isEqualTo(3)

        receiveChannel.poll()
        assertThat(size.get()).isEqualTo(2)

        receiveChannel.poll()
        assertThat(size.get()).isEqualTo(1)

        receiveChannel.poll()
        assertThat(size.get()).isEqualTo(0)

        upstream.close()
      }
    }
  }
}
