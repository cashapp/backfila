package app.cash.backfila.client

import com.squareup.wire.GrpcCall
import java.util.LinkedList
import java.util.Queue

/**
 * Implements request/response mocking for a gRPC client method. For instance, you can wire up your
 * fake client with a fake endpoint and factory:
 * ```kotlin
 *  class FakeMyServiceClient : MyServiceClient {
 *    val myEndpoint = FakeGrpcCallFactory<MyEndpointRequest, MyEndpointResponse>()
 *
 *    override fun MyEndpoint() : MyEndpointResponse = myEndpoint.call()
 *  }
 *  ```
 * Then in your test, you can register fake implementations for the endpoint
 * ```kotlin
 * fakeMyServiceClient.myEndpoint.enqueue { MyEndpointResponse(some = "value") }
 * ```
 * You can even throw an exception:
 * ```kotlin
 * fakeMyServiceClient.myEndpoint.enqueue { throw SomeError() }
 * ```
 *
 * If you want to see what requests your endpoint received, you may do so:
 * ```kotlin
 * fakeMyServiceClient.myEndpoint.takeRequests()
 * ```
 *
 * @param Req the request type
 * @param Res the response type
 * @param defaultImplementation the default implementation to use if no other implementations are
 * registered. By default, this will throw a [NotImplementedError].
 */
class FakeGrpcCallFactory<Req : Any, Res : Any>(
  private var defaultImplementation: GrpcImplementation<Req, Res> = {
    throw NotImplementedError("no default implementation for call factory")
  },
) {
  private val requests: Queue<Req> = LinkedList()

  private var implementations: Queue<GrpcImplementation<Req, Res>> =
    LinkedList()

  // usue the queue, otherwise, the default implementation
  private fun currentImplementation() = implementations.poll() ?: defaultImplementation

  /**
   * Register a function that takes in a [Req] and returns a [Res] to the caller. This is meant to
   * be configurable- you can implement some logic based on the request, or even just return a
   * response. Implementations will be invoked in the order in which they were enqueued.
   *
   * @param times number of times to register the request. Defaults to 1.
   * @param implementation the fake RPC implementation to invoke
   */
  fun enqueue(times: Int = 1, implementation: GrpcImplementation<Req, Res>) =
    repeat(times) { implementations.add(implementation) }

  /**
   * Set the default for this call factory. This will be used if no other implementations are
   * registered.
   *
   * @param implementation the fake RPC implementation to invoke by default
   */
  fun setDefault(implementation: GrpcImplementation<Req, Res>) {
    defaultImplementation = implementation
  }

  /**
   * Invoke the next fake RPC implementation that was registered
   */
  fun call() = GrpcCall { request: Req ->
    // Record the request
    requests += request

    currentImplementation().invoke(request)
  }

  /**
   * Fetch all of the recorded requests
   */
  fun takeRequests() = requests.toList()
}

typealias GrpcImplementation<Req, Res> = (Req) -> Res
