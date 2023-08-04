package app.cash.backfila.client

object Connectors {
  const val HTTP = "HTTP"
  const val ENVOY = "ENVOY"
}

data class HttpHeader(val name: String, val value: String)

data class HttpConnectorData(val url: String, val headers: List<HttpHeader> = listOf())

data class EnvoyConnectorData(val clusterType: String, val headers: List<HttpHeader> = listOf())
