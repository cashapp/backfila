package app.cash.backfila.client.misk.base

import app.cash.backfila.protos.service.Parameter
import okio.ByteString

data class BackfillConfig(
  val parameters: Map<String,ByteString>,
  val dryRun: Boolean = true
)