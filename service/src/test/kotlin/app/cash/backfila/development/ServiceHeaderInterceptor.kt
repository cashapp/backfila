package app.cash.backfila.development

import app.cash.backfila.client.BackfilaApi
import jakarta.inject.Inject
import jakarta.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

/**
 * Allows Development services to provide Backfila an authorized name.
 */
@Singleton
class ServiceHeaderInterceptor @Inject constructor(
  private val serviceName: String,
) : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
    val invocation = request.tag(Invocation::class.java)

    if (invocation?.method()?.declaringClass != BackfilaApi::class.java) {
      // Don't do anything if this isn't a Backfila API call!
      return chain.proceed(request)
    }

    val authorizedRequest = chain.request()
      .newBuilder()
      .header("X-Forwarded-Service", serviceName)
      .build()

    return chain.proceed(authorizedRequest)
  }
}
