package app.cash.backfila.client.misk

import okio.ByteString

data class BackfillConfig(
  val parameters: Map<String, ByteString>,
  val dryRun: Boolean = true
)
