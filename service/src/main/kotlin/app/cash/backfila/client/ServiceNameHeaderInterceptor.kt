package app.cash.backfila.client

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds service name header to HTTP requests for service identification.
 * This allows downstream systems to identify which service made the request.
 */
class ServiceNameHeaderInterceptor(
  private val serviceName: String,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
      .newBuilder()
      .header("X-Forwarded-Service", serviceName)
      .build()

    return chain.proceed(request)
  }
}
