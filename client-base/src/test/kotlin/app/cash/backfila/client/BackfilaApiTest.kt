package app.cash.backfila.client

import app.cash.backfila.client.fixedset.FixedSetDatastore
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createDryRun
import app.cash.backfila.embedded.createWetRun
import app.cash.backfila.protos.service.CheckBackfillStatusRequest
import app.cash.backfila.protos.service.CheckBackfillStatusResponse.Status.COMPLETE
import app.cash.backfila.protos.service.CheckBackfillStatusResponse.Status.RUNNING
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import com.google.inject.Module
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class BackfilaApiTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = TestingModule()

  @Inject lateinit var backfila: Backfila

  @Inject lateinit var backfilaApi: BackfilaApi

  @Inject lateinit var datastore: FixedSetDatastore

  @Test fun `createAndStartBackfill happy path`() {
    datastore.put("instance", "a", "b", "c")

    val createResponse = backfilaApi.createAndStartbackfill(
      CreateAndStartBackfillRequest(
        CreateBackfillRequest.Builder()
          .backfill_name(ToUpperCaseBackfill::class.java.name)
          .dry_run(false) // wet run
          .build(),
        null,
      ),
    ).execute()
    val backfillRunId = createResponse.body()!!.backfill_run_id
    val backfillRun = backfila.findExistingRun(ToUpperCaseBackfill::class, backfillRunId)
    // Check that the backfill worked.
    assertThat(backfillRun.backfill.runOrder).containsExactly("a", "b", "c")
    assertThat(datastore.valuesToList()).containsExactly("A", "B", "C")
    val statusResponse = backfilaApi.checkBackfillStatus(CheckBackfillStatusRequest(backfillRunId)).execute().body()!!
    assertThat(statusResponse.status).isEqualTo(COMPLETE)
  }

  @Test fun `find latest backfill`() {
    // Create a dry run and then a wet run.
    val dryRun = backfila.createDryRun<ToUpperCaseBackfill>()
    val wetRun = backfila.createWetRun<ToUpperCaseBackfill>()
    assertThat(dryRun.backfillRunId.toLong()).isLessThan(wetRun.backfillRunId.toLong())

    // The wet run should be the latest.
    val foundRun = backfila.findLatestRun(ToUpperCaseBackfill::class)
    assertThat(foundRun.dryRun).isFalse()
    assertThat(foundRun.backfillRunId).isEqualTo(wetRun.backfillRunId)
  }

  @Test fun `find started backfill`() {
    datastore.put("instance", "a", "b", "c")

    val backfillRun = backfila.createWetRun<ToUpperCaseBackfill>()
    // Check that the backfill hasn't started.
    assertThat(backfillRun.backfill.runOrder).isEmpty()
    val runningResponse = backfilaApi.checkBackfillStatus(CheckBackfillStatusRequest(backfillRun.backfillRunId.toLong())).execute().body()!!
    assertThat(runningResponse.status).isEqualTo(RUNNING)

    // Now finish the backfill
    backfillRun.execute()
    assertThat(backfillRun.backfill.runOrder).isNotEmpty()
    val completeResponse = backfilaApi.checkBackfillStatus(CheckBackfillStatusRequest(backfillRun.backfillRunId.toLong())).execute().body()!!
    assertThat(completeResponse.status).isEqualTo(COMPLETE)
  }

  @Test fun `test edge cases`() {
    datastore.put("instance", "a", "b", "c")

    val createResponse = backfilaApi.createAndStartbackfill(
      CreateAndStartBackfillRequest(
        CreateBackfillRequest.Builder()
          .backfill_name(ToUpperCaseBackfill::class.java.name)
          .dry_run(false) // wet run
          .build(),
        null,
      ),
    ).execute()
    val backfillRunId = createResponse.body()!!.backfill_run_id

    with(SoftAssertions()) {
      assertThatCode {
        backfila.findExistingRun(ToUpperCaseBackfill::class, backfillRunId + 1)
      }.hasMessageContaining("No Backfill with id ${backfillRunId + 1} found")

      assertThatCode {
        backfila.findExistingRun(ToLowerCaseBackfill::class, backfillRunId)
      }.hasMessageContaining("Backfill with run id $backfillRunId is not of type class ${ToLowerCaseBackfill::class.qualifiedName}")

      assertThatCode {
        backfilaApi.checkBackfillStatus(CheckBackfillStatusRequest(backfillRunId + 1)).execute()
      }.hasMessageContaining("No Backfill with id ${backfillRunId + 1} found")

      assertAll()
    }
  }
}
