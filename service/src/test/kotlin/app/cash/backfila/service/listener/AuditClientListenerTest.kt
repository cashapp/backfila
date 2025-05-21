package app.cash.backfila.service.listener

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.RunPartitionQuery
import com.google.inject.Module
import jakarta.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import misk.audit.FakeAuditClient
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class AuditClientListenerTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var createBackfillAction: CreateBackfillAction

  @Inject
  lateinit var scope: ActionScope

  @Inject
  lateinit var queryFactory: Query.Factory

  @Inject
  @BackfilaDb
  lateinit var transacter: Transacter

  @Inject
  lateinit var auditClientListener: AuditClientListener

  @Inject
  lateinit var fakeAuditClient: FakeAuditClient

  @Test
  fun `happy path`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      transacter.transaction { session ->
        val run = queryFactory.newQuery<BackfillRunQuery>().uniqueResult(session)
        assertNotNull(run)
        assertThat(run.state).isEqualTo(BackfillState.PAUSED)
        assertThat(run.created_by_user).isEqualTo("molly")
        assertThat(run.approved_by_user).isNull()
        assertThat(run.approved_at).isNull()
        assertThat(run.parameters()).isEmpty()
        assertThat(response.backfill_run_id).isEqualTo(run.id.id)

        val partitions = queryFactory.newQuery<RunPartitionQuery>()
          .backfillRunId(run.id)
          .orderByName()
          .list(session)
        assertThat(partitions).hasSize(2)
        assertThat(partitions[0].partition_name).isEqualTo("-80")
        assertThat(partitions[0].lease_token).isNull()
        assertThat(partitions[0].run_state).isEqualTo(BackfillState.PAUSED)
        assertThat(partitions[1].partition_name).isEqualTo("80-")
        assertThat(partitions[1].lease_token).isNull()
        assertThat(partitions[1].run_state).isEqualTo(BackfillState.PAUSED)
      }

      assertEquals(0, fakeAuditClient.sentEvents.size)
      auditClientListener.runStarted(Id(response.backfill_run_id), "molly")
      assertEquals(1, fakeAuditClient.sentEvents.size)
      assertEquals(
        FakeAuditClient.FakeAuditEvent(
          eventSource = "backfila",
          eventTarget = "ChickenSandwich",
          timestampSent = 2147483647,
          applicationName = "deep-fryer",
          // Fix fake client to not provide approver unless explicitly present
          approverLDAP = null,
          automatedChange = false,
          description = "Backfill started by molly [dryRun=true][service=deep-fryer][backfill=ChickenSandwich][id=${response.backfill_run_id}]",
          richDescription = null,
          environment = "testing",
          detailURL = "/backfills/${response.backfill_run_id}",
          region = "us-west-2",
          requestorLDAP = "molly",
        ),
        fakeAuditClient.sentEvents.last(),
      )

      auditClientListener.runPaused(Id(response.backfill_run_id), "molly")
      assertEquals(2, fakeAuditClient.sentEvents.size)
      assertEquals(
        FakeAuditClient.FakeAuditEvent(
          eventSource = "backfila",
          eventTarget = "ChickenSandwich",
          timestampSent = 2147483647,
          applicationName = "deep-fryer",
          approverLDAP = null,
          automatedChange = false,
          description = "Backfill paused by molly [dryRun=true][service=deep-fryer][backfill=ChickenSandwich][id=${response.backfill_run_id}]",
          richDescription = null,
          environment = "testing",
          detailURL = "/backfills/${response.backfill_run_id}",
          region = "us-west-2",
          requestorLDAP = "molly",
        ),
        fakeAuditClient.sentEvents.last(),
      )

      auditClientListener.runErrored(Id(response.backfill_run_id))
      assertEquals(3, fakeAuditClient.sentEvents.size)
      assertEquals(
        FakeAuditClient.FakeAuditEvent(
          eventSource = "backfila",
          eventTarget = "ChickenSandwich",
          timestampSent = 2147483647,
          applicationName = "deep-fryer",
          approverLDAP = null,
          automatedChange = true,
          description = "Backfill paused due to error [dryRun=true][service=deep-fryer][backfill=ChickenSandwich][id=${response.backfill_run_id}]",
          richDescription = null,
          environment = "testing",
          detailURL = "/backfills/${response.backfill_run_id}",
          region = "us-west-2",
          requestorLDAP = null,
        ),
        fakeAuditClient.sentEvents.last(),
      )

      auditClientListener.runCompleted(Id(response.backfill_run_id))
      assertEquals(4, fakeAuditClient.sentEvents.size)
      assertEquals(
        FakeAuditClient.FakeAuditEvent(
          eventSource = "backfila",
          eventTarget = "ChickenSandwich",
          timestampSent = 2147483647,
          applicationName = "deep-fryer",
          approverLDAP = null,
          automatedChange = true,
          description = "Backfill completed [dryRun=true][service=deep-fryer][backfill=ChickenSandwich][id=${response.backfill_run_id}]",
          richDescription = null,
          environment = "testing",
          detailURL = "/backfills/${response.backfill_run_id}",
          region = "us-west-2",
          requestorLDAP = null,
        ),
        fakeAuditClient.sentEvents.last(),
      )
    }
  }
}
