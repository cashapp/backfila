package app.cash.backfila.client

object Connectors {
  const val HTTP = "HTTP"
  const val ENVOY = "ENVOY"
  const val GRPC = "GRPC"
}

data class HttpHeader(val name: String, val value: String)

data class HttpConnectorData @JvmOverloads constructor(val url: String, val headers: List<HttpHeader> = listOf())

data class EnvoyConnectorData @JvmOverloads constructor(val clusterType: String, val headers: List<HttpHeader> = listOf())

data class GrpcConnectorData @JvmOverloads constructor(val url: String, val headers: List<HttpHeader> = listOf())
