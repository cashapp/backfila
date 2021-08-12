package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.FakeBackfilaClientServiceClient
import app.cash.backfila.dashboard.CloneBackfillAction
import app.cash.backfila.dashboard.CloneBackfillRequest
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.dashboard.RangeCloneType
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.protos.service.Parameter
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.RunPartitionQuery
import com.google.inject.Module
import javax.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class CloneBackfillActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var cloneBackfillAction: CloneBackfillAction

  @Inject
  lateinit var createBackfillAction: CreateBackfillAction

  @Inject
  lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject
  lateinit var scope: ActionScope

  @Inject
  lateinit var queryFactory: Query.Factory

  @Inject
  @BackfilaDb
  lateinit var transacter: Transacter

  @Inject
  lateinit var fakeBackfilaClientServiceClient: FakeBackfilaClientServiceClient

  @BeforeEach
  fun setup() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData.Builder()
                .name("ChickenSandwich")
                .parameters(
                  listOf(
                    Parameter.Builder()
                      .name("param1")
                      .build()
                  )
                )
                .build()
            )
          )
          .connector_type(Connectors.ENVOY)
          .build()
      )
    }
  }

  @Test
  fun `different values`() {
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .batch_size(100)
          .scan_size(200)
          .backoff_schedule("1000")
          .num_threads(1)
          .dry_run(true)
          .extra_sleep_ms(10)
          .parameter_map(mapOf("param1" to "val1".encodeUtf8()))
          .build()
      )

      val cloneResponse = cloneBackfillAction.create(
        response.backfill_run_id,
        CloneBackfillRequest(
          batch_size = 123,
          scan_size = 223,
          backoff_schedule = "1000,2000",
          num_threads = 5,
          dry_run = false,
          extra_sleep_ms = 15,
          parameter_map = mapOf("param1" to "val2".encodeUtf8()),
          range_clone_type = RangeCloneType.RESTART
        )
      )

      val status = getBackfillStatusAction.status(cloneResponse.id)
      assertThat(status.state).isEqualTo(BackfillState.PAUSED)
      assertThat(status.created_by_user).isEqualTo("molly")
      assertThat(status.parameters!!["param1"]).isEqualTo("val2")
      assertThat(status.dry_run).isFalse()
      assertThat(status.batch_size).isEqualTo(123)
      assertThat(status.scan_size).isEqualTo(223)
      assertThat(status.backoff_schedule).isEqualTo("1000,2000")
      assertThat(status.extra_sleep_ms).isEqualTo(15)
    }
  }

  @Test
  fun `new range`() {
    scope.fakeCaller(user = "molly") {
      fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
        PrepareBackfillResponse.Builder()
          .partitions(
            listOf(
              PrepareBackfillResponse.Partition.Builder()
                .partition_name("only")
                .backfill_range(KeyRange("0".encodeUtf8(), "1000".encodeUtf8()))
                .build()
            )
          )
          .build()
      )

      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )

      // Simulate progress
      transacter.transaction { session ->
        val partition = queryFactory.newQuery<RunPartitionQuery>()
          .uniqueResult(session)!!
        partition.pkey_cursor = "123".encodeUtf8()
      }

      // Return a different range the second time.
      fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
        PrepareBackfillResponse.Builder()
          .partitions(
            listOf(
              PrepareBackfillResponse.Partition.Builder()
                .partition_name("only")
                .backfill_range(KeyRange("2000".encodeUtf8(), "3000".encodeUtf8()))
                .build()
            )
          )
          .build()
      )

      val cloneResponse = cloneBackfillAction.create(
        response.backfill_run_id,
        CloneBackfillRequest(
          range_clone_type = RangeCloneType.NEW
        )
      )

      val status = getBackfillStatusAction.status(cloneResponse.id)
      val partition = status.partitions.single()
      assertThat(partition.name).isEqualTo("only")
      assertThat(partition.pkey_start).isEqualTo("2000")
      assertThat(partition.pkey_end).isEqualTo("3000")
      assertThat(partition.pkey_cursor).isNull()
    }
  }

  @Test
  fun `continue range`() {
    scope.fakeCaller(user = "molly") {
      fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
        PrepareBackfillResponse.Builder()
          .partitions(
            listOf(
              PrepareBackfillResponse.Partition.Builder()
                .partition_name("only")
                .backfill_range(KeyRange("0".encodeUtf8(), "1000".encodeUtf8()))
                .build()
            )
          )
          .build()
      )

      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )

      // Simulate progress
      transacter.transaction { session ->
        val partition = queryFactory.newQuery<RunPartitionQuery>()
          .uniqueResult(session)!!
        partition.pkey_cursor = "123".encodeUtf8()
      }

      // Return a different range the second time.
      fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
        PrepareBackfillResponse.Builder()
          .partitions(
            listOf(
              PrepareBackfillResponse.Partition.Builder()
                .partition_name("only")
                .backfill_range(KeyRange("2000".encodeUtf8(), "3000".encodeUtf8()))
                .build()
            )
          )
          .build()
      )

      val cloneResponse = cloneBackfillAction.create(
        response.backfill_run_id,
        CloneBackfillRequest(
          range_clone_type = RangeCloneType.CONTINUE
        )
      )

      val status = getBackfillStatusAction.status(cloneResponse.id)
      val partition = status.partitions.single()
      assertThat(partition.name).isEqualTo("only")
      assertThat(partition.pkey_start).isEqualTo("0")
      assertThat(partition.pkey_end).isEqualTo("1000")
      assertThat(partition.pkey_cursor).isEqualTo("123")
    }
  }

  @Test
  fun `restart range`() {
    scope.fakeCaller(user = "molly") {
      fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
        PrepareBackfillResponse.Builder()
          .partitions(
            listOf(
              PrepareBackfillResponse.Partition.Builder()
                .partition_name("only")
                .backfill_range(KeyRange("0".encodeUtf8(), "1000".encodeUtf8()))
                .build()
            )
          )
          .build()
      )

      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )

      // Simulate progress
      transacter.transaction { session ->
        val partition = queryFactory.newQuery<RunPartitionQuery>()
          .uniqueResult(session)!!
        partition.pkey_cursor = "123".encodeUtf8()
      }

      // Return a different range the second time.
      fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
        PrepareBackfillResponse.Builder()
          .partitions(
            listOf(
              PrepareBackfillResponse.Partition.Builder()
                .partition_name("only")
                .backfill_range(KeyRange("2000".encodeUtf8(), "3000".encodeUtf8()))
                .build()
            )
          )
          .build()
      )

      val cloneResponse = cloneBackfillAction.create(
        response.backfill_run_id,
        CloneBackfillRequest(
          range_clone_type = RangeCloneType.RESTART
        )
      )

      val status = getBackfillStatusAction.status(cloneResponse.id)
      val partition = status.partitions.single()
      assertThat(partition.name).isEqualTo("only")
      assertThat(partition.pkey_start).isEqualTo("0")
      assertThat(partition.pkey_end).isEqualTo("1000")
      assertThat(partition.pkey_cursor).isNull()
    }
  }

  @Test
  fun `restart range but partitions are different`() {
    scope.fakeCaller(user = "molly") {
      fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
        PrepareBackfillResponse.Builder()
          .partitions(
            listOf(
              PrepareBackfillResponse.Partition.Builder()
                .partition_name("only")
                .backfill_range(KeyRange("0".encodeUtf8(), "1000".encodeUtf8()))
                .build()
            )
          )
          .build()
      )

      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )

      // Simulate progress
      transacter.transaction { session ->
        val partition = queryFactory.newQuery<RunPartitionQuery>()
          .uniqueResult(session)!!
        partition.pkey_cursor = "123".encodeUtf8()
      }

      // Return a different range the second time.
      fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
        PrepareBackfillResponse.Builder()
          .partitions(
            listOf(
              PrepareBackfillResponse.Partition.Builder()
                .partition_name("different-name")
                .backfill_range(KeyRange("2000".encodeUtf8(), "3000".encodeUtf8()))
                .build()
            )
          )
          .build()
      )

      assertThatThrownBy {
        cloneBackfillAction.create(
          response.backfill_run_id,
          CloneBackfillRequest(
            range_clone_type = RangeCloneType.RESTART
          )
        )
      }.hasMessageContaining("partitions don't match")
    }
  }

  @Test
  fun `new range but partitions are different is ok`() {
    scope.fakeCaller(user = "molly") {
      fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
        PrepareBackfillResponse.Builder()
          .partitions(
            listOf(
              PrepareBackfillResponse.Partition.Builder()
                .partition_name("only")
                .backfill_range(KeyRange("0".encodeUtf8(), "1000".encodeUtf8()))
                .build()
            )
          )
          .build()
      )

      val response = createBackfillAction.create(
        "deep-fryer",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build()
      )

      // Simulate progress
      transacter.transaction { session ->
        val partition = queryFactory.newQuery<RunPartitionQuery>()
          .uniqueResult(session)!!
        partition.pkey_cursor = "123".encodeUtf8()
      }

      // Return a different range the second time.
      fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
        PrepareBackfillResponse.Builder()
          .partitions(
            listOf(
              PrepareBackfillResponse.Partition.Builder()
                .partition_name("different-name")
                .backfill_range(KeyRange("2000".encodeUtf8(), "3000".encodeUtf8()))
                .build()
            )
          )
          .build()
      )

      val cloneResponse = cloneBackfillAction.create(
        response.backfill_run_id,
        CloneBackfillRequest(
          range_clone_type = RangeCloneType.NEW
        )
      )

      val status = getBackfillStatusAction.status(cloneResponse.id)
      val partition = status.partitions.single()
      assertThat(partition.name).isEqualTo("different-name")
      assertThat(partition.pkey_start).isEqualTo("2000")
      assertThat(partition.pkey_end).isEqualTo("3000")
      assertThat(partition.pkey_cursor).isNull()
    }
  }
}
