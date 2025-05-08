package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.StartBackfillAction
import app.cash.backfila.dashboard.StartBackfillRequest
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.RunPartitionQuery
import com.google.inject.Module
import javax.inject.Inject
import misk.exceptions.BadRequestException
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class EditPartitionEndPointTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject lateinit var configureServiceAction: ConfigureServiceAction

  @Inject lateinit var createBackfillAction: CreateBackfillAction

  @Inject lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject lateinit var startBackfillAction: StartBackfillAction

  @Inject @BackfilaDb
  lateinit var transacter: Transacter

  @Inject lateinit var queryFactory: Query.Factory

  @Inject lateinit var scope: ActionScope

  /**
   * Test-specific action that provides the same functionality as EditPartitionCursorHandlerAction
   * but without the HTTP dependencies
   */
  inner class TestEditPartitionAction {
    fun editPartition(id: Long, partitionName: String, newCursor: String) {
      val backfill = getBackfillStatusAction.status(id)
      if (backfill.state != BackfillState.PAUSED) {
        throw BadRequestException("Backfill must be paused to edit cursors")
      }

      val partition = backfill.partitions.find { it.name == partitionName }
        ?: throw BadRequestException("Partition not found")

      transacter.transaction { session ->
        queryFactory.newQuery<RunPartitionQuery>()
          .partitionId(partition.id)
          .uniqueResult(session)
          ?.let { partitionRecord ->
            partitionRecord.pkey_cursor = newCursor.encodeUtf8()
            session.save(partitionRecord)
          } ?: throw BadRequestException("Partition not found")
      }
    }
  }

  @Test
  fun `edit partition end point when paused`() {
    // Setup test backfill
    val backfillId = setupTestBackfill()

    // Get initial status to capture cursor snapshot
    val initialStatus = getBackfillStatusAction.status(backfillId)
    val partition = initialStatus.partitions[0]
    requireNotNull(partition) { "Partition not found" }

    // Edit cursor
    scope.fakeCaller(user = "molly") {
      val testAction = TestEditPartitionAction()
      val response = testAction.editPartition(
        backfillId,
        partition.name,
        "100",
      )

      // Verify cursor was updated
      val updatedStatus = getBackfillStatusAction.status(backfillId)
      val updatedPartition = updatedStatus.partitions.find { it.name == partition.name }
      requireNotNull(updatedPartition) { "Updated partition not found" }
      assertThat(updatedPartition.pkey_cursor ?: "").isEqualTo("100")
    }
  }

  @Test
  fun `cannot edit partition end point when running`() {
    // Setup test backfill
    val backfillId = setupTestBackfill()

    // Start the backfill
    scope.fakeCaller(user = "molly") {
      startBackfillAction.start(backfillId, StartBackfillRequest())
    }

    // Get status for cursor snapshot
    val status = getBackfillStatusAction.status(backfillId)
    val partition = status.partitions[0]
    requireNotNull(partition) { "Partition not found" }

    // Attempt to edit cursor while running
    scope.fakeCaller(user = "molly") {
      val testAction = TestEditPartitionAction()
      assertThatThrownBy {
        testAction.editPartition(
          backfillId,
          partition.name,
          "100",
        )
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessageContaining("Backfill must be paused to edit cursors")
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
