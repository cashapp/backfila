package app.cash.backfila.client

import app.cash.backfila.embedded.Backfila
import app.cash.backfila.client.fixedset.FixedSetBackfill
import app.cash.backfila.client.fixedset.FixedSetDatastore
import app.cash.backfila.client.fixedset.FixedSetRow
import com.google.inject.Module
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset.UTC

@MiskTest(startService = true)
class DeleteBackfillByTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = TestingModule()

  @Inject lateinit var backfila: Backfila
  @Inject lateinit var datastore: FixedSetDatastore

  @DeleteBy("2031-01-02")
  class TenYearBackfill @Inject constructor() : FixedSetBackfill<NoParameters>() {
    override fun runOne(row: FixedSetRow) {
      // We won't be running this one
    }

    override fun checkBackfillConfig(backfillConfig: BackfillConfig<NoParameters>) {
      // No parameters to check
    }
  }

  class DeprecatedBackfill @Inject constructor() : FixedSetBackfill<NoParameters>() {
    override fun runOne(row: FixedSetRow) {
      // We won't be running this one
    }

    override fun checkBackfillConfig(backfillConfig: BackfillConfig<NoParameters>) {
      // No parameters to check
    }
  }

  @Test fun `check that the delete_by was set`() {
    assertThat(backfila.configureServiceData).isNotNull
    assertThat(backfila.configureServiceData!!.backfills)
      .filteredOn { it.name.endsWith(TenYearBackfill::class.java.name) }.singleElement()
    val tenYearBackfillData = backfila.configureServiceData!!.backfills
      .filter { it.name.endsWith(TenYearBackfill::class.java.name) }.single()
    assertThat(tenYearBackfillData.delete_by)
      .isEqualTo(LocalDate.parse("2031-01-02").atStartOfDay().toInstant(UTC).toEpochMilli())
  }

  @Test fun `check that the delete_by is null when not set`() {
    assertThat(backfila.configureServiceData).isNotNull
    assertThat(backfila.configureServiceData!!.backfills)
      .filteredOn { it.name.endsWith(DeprecatedBackfill::class.java.name) }.singleElement()
    val deprecatedBackfill = backfila.configureServiceData!!.backfills
      .filter { it.name.endsWith(DeprecatedBackfill::class.java.name) }.single()
    assertNull(deprecatedBackfill.delete_by)
  }
}
