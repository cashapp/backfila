package app.cash.backfila.client.fixedset

import app.cash.backfila.client.Backfill
import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.ValidateResult

abstract class FixedSetBackfill<Param : Any> : Backfill {
  open fun checkBackfillConfig(backfillConfig: BackfillConfig<Param>): ValidateResult<Param> {
    return ValidateResult(backfillConfig.parameters)
  }

  abstract fun runOne(row: FixedSetRow)
}
