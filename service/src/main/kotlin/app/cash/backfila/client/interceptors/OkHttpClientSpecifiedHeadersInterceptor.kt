package app.cash.backfila.client.interceptors

import app.cash.backfila.client.HttpHeader
import okhttp3.Interceptor
import okhttp3.Response

internal class OkHttpClientSpecifiedHeadersInterceptor internal constructor(
  private val headers: List<HttpHeader>,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    if (headers.isEmpty()) {
      return chain.proceed(chain.request())
    }

    val requestBuilder = chain.request().newBuilder()
    for (header in headers) {
      requestBuilder.header(header.name, header.value)
    }
    return chain.proceed(requestBuilder.build())
  }

  companion object {
    const val MAX_ALLOWED_HEADERS_SIZE = 2048

    fun headersSizeWithinLimit(headers: List<HttpHeader>): Boolean {
      return headers.joinToString("\r\n") { header -> "${header.name}: ${header.value}" }
        .toByteArray()
        .size <= MAX_ALLOWED_HEADERS_SIZE
    }
  }
}
