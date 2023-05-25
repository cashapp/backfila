package app.cash.backfila.client

data class BackfillConfig<Param : Any>(
  val parameters: Param,
  val partitionName: String,
  val backfillId: String,
  val dryRun: Boolean,
) {
  fun prepareConfig() = PrepareBackfillConfig(parameters, dryRun)
}

data class PrepareBackfillConfig<Param : Any>(
  val parameters: Param,
  val dryRun: Boolean,
)
