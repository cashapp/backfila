package app.cash.backfila.client.interceptors

import okhttp3.Interceptor
import okhttp3.Response

// See the Envoy Client Specification - https://docs.google.com/document/d/1zKK12F1tesOcDDm8GnYCZbQAI-MlVwtyRxDYr8SJ_5w/edit#heading=h.kl0yk0omo76q
private const val X_SQ_ENVOY_GNS_LABEL = "X-Sq-Envoy-Gns-Label"

class OkHttpGnsLabelInterceptor constructor(
  private val label: String,
) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    val request = chain.request()
      .newBuilder()
      .header(X_SQ_ENVOY_GNS_LABEL, label)
      .build()
    return chain.proceed(request)
  }
}
