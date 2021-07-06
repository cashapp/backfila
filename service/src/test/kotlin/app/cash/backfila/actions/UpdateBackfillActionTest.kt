package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.UpdateBackfillAction
import app.cash.backfila.dashboard.UpdateBackfillRequest
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import com.google.inject.Module
import javax.inject.Inject
import misk.exceptions.BadRequestException
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class UpdateBackfillActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var createBackfillAction: CreateBackfillAction

  @Inject
  lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject
  lateinit var updateBackfillAction: UpdateBackfillAction

  @Inject
  lateinit var scope: ActionScope

  @Test
  fun update() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null
              )
            )
          )
          .connector_type(Connectors.ENVOY)
          .build()
      )
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )

      val id = response.backfill_run_id

      updateBackfillAction.update(id, UpdateBackfillRequest(num_threads = 12))
      var status = getBackfillStatusAction.status(id)
      assertThat(status.event_logs[0].message).isEqualTo("updated settings: num_threads 1->12")
      assertThat(status.event_logs[0].user).isEqualTo("molly")

      updateBackfillAction.update(id, UpdateBackfillRequest(scan_size = 2000))
      status = getBackfillStatusAction.status(id)
      assertThat(status.event_logs[0].message).isEqualTo("updated settings: scan_size 1000->2000")
      assertThat(status.event_logs[0].user).isEqualTo("molly")

      updateBackfillAction.update(id, UpdateBackfillRequest(batch_size = 10))
      status = getBackfillStatusAction.status(id)
      assertThat(status.event_logs[0].message).isEqualTo("updated settings: batch_size 100->10")
      assertThat(status.event_logs[0].user).isEqualTo("molly")

      updateBackfillAction.update(id, UpdateBackfillRequest(backoff_schedule = "1000,2000"))
      status = getBackfillStatusAction.status(id)
      assertThat(status.event_logs[0].message).isEqualTo("updated settings: backoff_schedule null->1000,2000")
      assertThat(status.event_logs[0].user).isEqualTo("molly")

      updateBackfillAction.update(id, UpdateBackfillRequest(extra_sleep_ms = 10))
      status = getBackfillStatusAction.status(id)
      assertThat(status.event_logs[0].message).isEqualTo("updated settings: extra_sleep_ms 0->10")
      assertThat(status.event_logs[0].user).isEqualTo("molly")
    }
  }

  @Test
  fun `scan size must be at least batch size`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null
              )
            )
          )
          .connector_type(Connectors.ENVOY)
          .build()
      )
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )

      val id = response.backfill_run_id

      assertThatThrownBy {
        updateBackfillAction.update(id, UpdateBackfillRequest(scan_size = 12))
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessageContaining("scan_size must be >= batch_size")

      assertThatThrownBy {
        updateBackfillAction.update(id, UpdateBackfillRequest(batch_size = 10000))
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessageContaining("scan_size must be >= batch_size")
    }
  }

  @Test
  fun `minimum values`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null
              )
            )
          )
          .connector_type(Connectors.ENVOY)
          .build()
      )
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )

      val id = response.backfill_run_id

      assertThatThrownBy {
        updateBackfillAction.update(id, UpdateBackfillRequest(scan_size = 0))
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessageContaining("scan_size must be >= 1")

      assertThatThrownBy {
        updateBackfillAction.update(id, UpdateBackfillRequest(batch_size = 0))
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessageContaining("batch_size must be >= 1")

      assertThatThrownBy {
        updateBackfillAction.update(id, UpdateBackfillRequest(num_threads = 0))
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessageContaining("num_threads must be >= 1")

      assertThatThrownBy {
        updateBackfillAction.update(id, UpdateBackfillRequest(extra_sleep_ms = -1))
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessageContaining("extra_sleep_ms must be >= 0")
    }
  }
}
