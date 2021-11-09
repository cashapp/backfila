package app.cash.backfila.service.runner.statemachine

import java.util.LinkedList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

class VariableCapacityChannel<T>(
  capacity: Int
) {
  /**
   * Can be changed at any time. However, if the buffer is full (i.e. upstream is blocked on send()),
   * this won't take effect until the next time downstream channel receives.
   */
  var capacity: Int
    get() = _capacity
    set(value) = if (value < 1) {
      throw IllegalArgumentException("capacity must be > 0")
    } else {
      _capacity = value
    }
  private var _capacity = capacity

  // Initialize it ourselves since it must be a rendezvous channel.
  private val upstream = Channel<T>()

  fun upstream(): SendChannel<T> = upstream

  private val downstream = Channel<T>()

  fun downstream(): ReceiveChannel<T> = downstream

  private val buffer = LinkedList<T>()

  fun queued() = buffer.size

  fun proxy(coroutineScope: CoroutineScope): ReceiveChannel<T> {
    coroutineScope.launch(CoroutineName("VariableCapacityChannel")) {
      var upstreamClosed = false
      while (!upstreamClosed) {
        try {
          select<Unit> {
            if (buffer.size > 0) {
              downstream.onSend(buffer.first()) {
                buffer.removeFirst()
              }
            }
            if (buffer.size < capacity) {
              upstream.onReceiveCatching {
                val value = it.getOrNull()
                if (value != null) {
                  buffer += value
                } else {
                  // Sender closed. Finish sending buffered items then close and exit.
                  while (buffer.size > 0) {
                    downstream.send(buffer.removeFirst())
                  }
                  // If the sender cancelled, the exception propogates it to the receiver.
                  // Otherwise this closes cleanly.
                  downstream.close(it.exceptionOrNull())
                  upstreamClosed = true
                }
              }
            }
          }
        } catch (e: CancellationException) {
          upstream.cancel(e)
          downstream.cancel(e)
          break
        }
      }
      downstream.close()
    }
    return downstream
  }
}
