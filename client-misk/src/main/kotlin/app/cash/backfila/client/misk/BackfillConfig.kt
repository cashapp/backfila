package app.cash.backfila.client.misk

data class BackfillConfig<Param : Any>(
  val parameters: Param,
  val dryRun: Boolean = true
)
