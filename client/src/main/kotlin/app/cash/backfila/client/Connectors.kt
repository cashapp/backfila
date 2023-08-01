package app.cash.backfila.client

object Connectors {
  const val HTTP = "HTTP"
  const val ENVOY = "ENVOY"
}

data class PerRunOverrideData(
  val overrideClusterType: String? = null,
)

data class HttpConnectorData(val url: String)

data class EnvoyConnectorData(val clusterType: String, val gnsLabel: String? = null)
