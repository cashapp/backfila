package app.cash.backfila.client.interceptors

import app.cash.backfila.client.HttpHeader
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OkHttpClientSpecifiedHeadersInterceptorTest {
  @Test
  fun requestHeadersTest() {
    val webServer = MockWebServer()
    webServer.enqueue(MockResponse().setBody("hello, world!"))
    webServer.start()
    val url = webServer.url("/services/squareup.backfila.clientservice.BackfilaClientService/")

    val request = Request.Builder()
      .url(url)
      .build()

    val headers: List<HttpHeader> = listOf(
      HttpHeader(name = "ChickenSandwich", value = "OK"),
      HttpHeader(name = "SpicyChickenSandwich", value = "PRETTYGOOD"),
    )

    val client = OkHttpClient()
      .newBuilder()
      .addInterceptor(OkHttpClientSpecifiedHeadersInterceptor(headers))
      .build()

    client.newCall(request).execute()

    val recordedRequest = webServer.takeRequest()
    assertThat(recordedRequest.getHeader("ChickenSandwich")).isEqualTo("OK")
    assertThat(recordedRequest.getHeader("SpicyChickenSandwich")).isEqualTo("PRETTYGOOD")

    client.dispatcher.executorService.shutdown()
    webServer.shutdown()
  }

  @Test
  fun requestHeadersTest_emptyHeadersList() {
    val webServer = MockWebServer()
    webServer.enqueue(MockResponse().setBody("hello, world!"))
    webServer.start()
    val url = webServer.url("/services/squareup.backfila.clientservice.BackfilaClientService/")

    val request = Request.Builder()
      .url(url)
      .build()

    val client = OkHttpClient()
      .newBuilder()
      .addInterceptor(OkHttpClientSpecifiedHeadersInterceptor(listOf()))
      .build()

    val response = client.newCall(request).execute()
    assertThat(response.body.string()).isEqualTo("hello, world!")

    client.dispatcher.executorService.shutdown()
    webServer.shutdown()
  }
}
