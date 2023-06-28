package app.cash.backfila.client

import app.cash.backfila.client.fixedset.FixedSetBackfill
import app.cash.backfila.client.fixedset.FixedSetRow
import java.util.Locale
import javax.inject.Inject

/**
 * Simple backfills to be used in tests.
 */
class ToUpperCaseBackfill @Inject constructor() : FixedSetBackfill<NoParameters>() {
  val runOrder = mutableListOf<String>()
  var seenBackfillId: String? = null
  override fun runOne(row: FixedSetRow, backfillConfig: BackfillConfig<NoParameters>) {
    seenBackfillId = backfillConfig.backfillId
    runOrder += row.value
    row.value = row.value.uppercase(Locale.ROOT)
  }
}

class ToLowerCaseBackfill @Inject constructor() : FixedSetBackfill<NoParameters>() {
  val runOrder = mutableListOf<String>()
  var seenBackfillId: String? = null
  override fun runOne(row: FixedSetRow, backfillConfig: BackfillConfig<NoParameters>) {
    seenBackfillId = backfillConfig.backfillId
    runOrder += row.value
    row.value = row.value.lowercase(Locale.ROOT)
  }
}
