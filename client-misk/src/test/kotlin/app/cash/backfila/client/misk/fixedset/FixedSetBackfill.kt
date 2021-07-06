package app.cash.backfila.client.misk.fixedset

import app.cash.backfila.client.Backfill
import app.cash.backfila.client.BackfillConfig

abstract class FixedSetBackfill<Param : Any> : Backfill {
  abstract fun checkBackfillConfig(backfillConfig: BackfillConfig<Param>)
  abstract fun runOne(row: FixedSetRow)
}
