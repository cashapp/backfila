package app.cash.backfila.client

data class BackfillConfig<Param : Any>(
  val parameters: Param,
  val partitionName: String,
  val dryRun: Boolean,
)

data class PrepareBackfillConfig<Param : Any>(
  val parameters: Param,
  val dryRun: Boolean,
)
