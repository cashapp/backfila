package app.cash.backfila.client

data class BackfillConfig<Param : Any>(
  val parameters: Param,
  val dryRun: Boolean = true
)
