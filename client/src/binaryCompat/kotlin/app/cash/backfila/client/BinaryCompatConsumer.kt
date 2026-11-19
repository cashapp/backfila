package app.cash.backfila.client

fun main() {
  val http = BackfilaHttpClientConfig(url = "http://localhost", slack_channel = "#channel")
  check(http.variant == null)
  check(http.copy(variant = "blue") == BackfilaHttpClientConfig("http://localhost", "#channel", "blue"))
  check(http.copy("http://other", null, "green") == BackfilaHttpClientConfig("http://other", null, "green"))

  val client = BackfilaClientConfig(
    slack_channel = "#channel",
    connector_type = Connectors.HTTP,
    connector_extra_data = "{}",
    variant = null,
  )
  check(client.copy(variant = "blue").variant == "blue")
  check(client.copy(connector_extra_data = "{\"a\":1}").connector_extra_data == "{\"a\":1}")
  check(client.copy("#other", Connectors.ENVOY, "{}", "green") == BackfilaClientConfig("#other", Connectors.ENVOY, "{}", "green"))
}
