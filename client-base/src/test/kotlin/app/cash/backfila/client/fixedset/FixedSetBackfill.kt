package app.cash.backfila.client.fixedset

import app.cash.backfila.client.Backfill
import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.FinalizeBackfillConfig
import app.cash.backfila.client.ValidateResult

abstract class FixedSetBackfill<Param : Any> : Backfill {
  open fun checkParameters(parameters: Param): ValidateResult<Param> {
    return ValidateResult(parameters)
  }

  abstract fun runOne(row: FixedSetRow, backfillConfig: BackfillConfig<Param>)

  /**
   * Override this to do any work after the backfill completes.
   */
  open fun finalize(config: FinalizeBackfillConfig<Param>) {}
}
