package app.cash.backfila.client

import app.cash.backfila.client.fixedset.FixedSetBackfill
import app.cash.backfila.client.fixedset.FixedSetDatastore
import app.cash.backfila.client.fixedset.FixedSetRow
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import com.google.inject.Module
import java.util.Locale
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class FixedSetTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = TestingModule()

  @Inject lateinit var backfila: Backfila

  @Inject lateinit var datastore: FixedSetDatastore

  class ToUpperCaseBackfill @Inject constructor() : FixedSetBackfill<NoParameters>() {
    val runOrder = mutableListOf<String>()
    var seenBackfillId: String? = null
    override fun runOne(row: FixedSetRow, backfillConfig: BackfillConfig<NoParameters>) {
      seenBackfillId = backfillConfig.backfillId
      runOrder += row.value
      row.value = row.value.toUpperCase(Locale.ROOT)
    }
  }

  @Test fun `happy path`() {
    datastore.put("instance", "a", "b", "c")

    val backfillRun = backfila.createWetRun<ToUpperCaseBackfill>()
    backfillRun.execute()
    assertThat(backfillRun.backfill.runOrder).containsExactly("a", "b", "c")
    assertThat(datastore.valuesToList()).containsExactly("A", "B", "C")
  }

  @Test fun `run two instance backfill`() {
    datastore.put("instance-1", "a", "b", "c")
    datastore.put("instance-2", "e", "f")

    val backfillRun = backfila.createWetRun<ToUpperCaseBackfill>()
    backfillRun.execute()
    assertThat(backfillRun.backfill.runOrder).containsExactly("a", "b", "c", "e", "f")
    assertThat(datastore.valuesToList()).containsExactly("A", "B", "C", "E", "F")
  }

  @Test fun `backfillId is a number`() {
    datastore.put("instance", "a", "b", "c")

    val backfillRun = backfila.createWetRun<ToUpperCaseBackfill>()
    backfillRun.execute()
    assertThat(backfillRun.backfill.seenBackfillId?.toInt()).isNotNull()
  }
}
