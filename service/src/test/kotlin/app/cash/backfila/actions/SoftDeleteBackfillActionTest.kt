package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.SoftDeleteBackfillAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import com.google.inject.Module
import javax.inject.Inject
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class SoftDeleteBackfillActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction

  @Inject lateinit var createBackfillAction: CreateBackfillAction

  @Inject lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject lateinit var softDeleteBackfillAction: SoftDeleteBackfillAction

  @Inject @BackfilaDb
  lateinit var transacter: Transacter

  @Inject lateinit var queryFactory: Query.Factory

  @Inject lateinit var scope: ActionScope

  @Test
  fun `soft delete completed backfill`() {
    // Setup test backfill
    val backfillId = setupTestBackfill()

    // Set state to COMPLETE
    transacter.transaction { session ->
      val backfill = session.load<DbBackfillRun>(Id(backfillId))
      backfill.setState(session, queryFactory, BackfillState.COMPLETE)
    }

    // Soft delete the backfill
    scope.fakeCaller(user = "molly") {
      softDeleteBackfillAction.softDelete(backfillId)
    }

    // Verify the backfill is soft deleted
    transacter.transaction { session ->
      val backfill = session.load<DbBackfillRun>(Id(backfillId))
      assertThat(backfill.deleted_at).isNotNull()
    }

    // Verify event log
    val status = getBackfillStatusAction.status(backfillId)
    assertThat(status.event_logs[0].message).isEqualTo("backfill soft deleted")
    assertThat(status.event_logs[0].user).isEqualTo("molly")
  }

  @Test
  fun `soft delete cancelled backfill`() {
    // Setup test backfill
    val backfillId = setupTestBackfill()

    // Set state to CANCELLED
    transacter.transaction { session ->
      val backfill = session.load<DbBackfillRun>(Id(backfillId))
      backfill.setState(session, queryFactory, BackfillState.CANCELLED)
    }

    // Soft delete the backfill
    scope.fakeCaller(user = "molly") {
      softDeleteBackfillAction.softDelete(backfillId)
    }

    // Verify the backfill is soft deleted
    transacter.transaction { session ->
      val backfill = session.load<DbBackfillRun>(Id(backfillId))
      assertThat(backfill.deleted_at).isNotNull()
    }
  }

  @Test
  fun `cannot soft delete running backfill`() {
    // Setup test backfill
    val backfillId = setupTestBackfill()

    // Set state to RUNNING
    transacter.transaction { session ->
      val backfill = session.load<DbBackfillRun>(Id(backfillId))
      backfill.setState(session, queryFactory, BackfillState.RUNNING)
    }

    // Attempt to soft delete running backfill
    scope.fakeCaller(user = "molly") {
      assertThatThrownBy {
        softDeleteBackfillAction.softDelete(backfillId)
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessageContaining("Can only delete completed or cancelled backfills")
    }

    // Verify the backfill is not deleted
    transacter.transaction { session ->
      val backfill = session.load<DbBackfillRun>(Id(backfillId))
      assertThat(backfill.deleted_at).isNull()
    }
  }

  @Test
  fun `cannot soft delete paused backfill`() {
    // Setup test backfill (default state is PAUSED)
    val backfillId = setupTestBackfill()

    // Attempt to soft delete paused backfill
    scope.fakeCaller(user = "molly") {
      assertThatThrownBy {
        softDeleteBackfillAction.softDelete(backfillId)
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessageContaining("Can only delete completed or cancelled backfills")
    }

    // Verify the backfill is not deleted
    transacter.transaction { session ->
      val backfill = session.load<DbBackfillRun>(Id(backfillId))
      assertThat(backfill.deleted_at).isNull()
    }
  }

  private fun setupTestBackfill(): Long {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }

    val response = scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        ConfigureServiceAction.RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )
    }

    return response.backfill_run_id
  }
}
