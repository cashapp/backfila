package app.cash.backfila.client

import app.cash.backfila.client.fixedset.FixedSetBackfill
import app.cash.backfila.client.fixedset.FixedSetDatastore
import app.cash.backfila.client.fixedset.FixedSetRow
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import com.google.inject.Module
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests modifying parameters by returning them in the PrepareBackfillResponse.
 */
@MiskTest(startService = true)
class PrepareWithParametersTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = TestingModule()

  @Inject lateinit var backfila: Backfila
  @Inject lateinit var datastore: FixedSetDatastore

  data class PrepareParameters(
    val favoriteNumber: Int? = null
  )

  class PrepareWithParametersBackfill @Inject constructor() : FixedSetBackfill<PrepareParameters>() {
    override fun checkBackfillConfig(
      backfillConfig: BackfillConfig<PrepareParameters>
    ): ValidateResult<PrepareParameters> {
      if (backfillConfig.parameters.favoriteNumber != null) {
        return ValidateResult(backfillConfig.parameters)
      }
      return ValidateResult(
        PrepareParameters(favoriteNumber = 42)
      )
    }

    override fun runOne(row: FixedSetRow, backfillConfig: BackfillConfig<PrepareParameters>) {
    }
  }

  @Test fun `add parameters from prepare response`() {
    datastore.put("instance", "a", "b", "c")

    val backfillRun = backfila.createWetRun<PrepareWithParametersBackfill>()
    backfillRun.execute()
    assertThat(backfillRun.parameters["favoriteNumber"]).isEqualTo("42".encodeUtf8())
  }

  @Test fun `keep original parameters`() {
    datastore.put("instance", "a", "b", "c")

    val backfillRun = backfila.createWetRun<PrepareWithParametersBackfill>(
      parameters = PrepareParameters(
        favoriteNumber = 12
      )
    )
    backfillRun.execute()
    assertThat(backfillRun.parameters["favoriteNumber"]).isEqualTo("12".encodeUtf8())
  }
}
