package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.BackfillCreator
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.FakeBackfilaCallbackConnector
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.protos.service.Parameter
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.RunPartitionQuery
import app.cash.backfila.service.persistence.ServiceQuery
import com.google.inject.Module
import javax.inject.Inject
import kotlin.test.assertNotNull
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
class CreateBackfillActionTest {
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
  lateinit var fakeBackfilaClientServiceClient: FakeBackfilaCallbackConnector

  @Test
  fun serviceDoesntExist() {
    scope.fakeCaller(user = "molly") {
      assertThatThrownBy {
        createBackfillAction.create(
          "deep-fryer",
          RESERVED_VARIANT,
          CreateBackfillRequest.Builder()
            .backfill_name("abc")
            .build(),
        )
      }.isInstanceOf(BadRequestException::class.java)
    }
  }

  @Test
  fun backfillDoesntExist() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }

    scope.fakeCaller(user = "molly") {
      assertThatThrownBy {
        createBackfillAction.create(
          "deep-fryer",
          RESERVED_VARIANT,
          CreateBackfillRequest.Builder()
            .backfill_name("abc")
            .build(),
        )
      }.isInstanceOf(BadRequestException::class.java)
    }
  }

  @Test
  fun created() {
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
    }
  }

  @Test
  fun `created with variant`() {
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

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .variant("deep-fried")
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
    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      transacter.transaction { session ->
        val service = queryFactory.newQuery<ServiceQuery>()
          .registryName("deep-fryer")
          .variant(RESERVED_VARIANT)
          .uniqueResult(session)

        val run = service?.let {
          queryFactory.newQuery<BackfillRunQuery>()
            .serviceId(it.id)
            .uniqueResult(session)
        }
        assertNotNull(run)
        assertThat(run.state).isEqualTo(BackfillState.PAUSED)
        assertThat(run.created_by_user).isEqualTo("molly")
        assertThat(run.approved_by_user).isNull()
        assertThat(run.approved_at).isNull()
        assertThat(run.service.variant).isEqualTo(RESERVED_VARIANT)
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
    }

    scope.fakeCaller(user = "molly") {
      val response = createBackfillAction.create(
        "deep-fryer",
        "deep-fried",
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      transacter.transaction { session ->

        val service = queryFactory.newQuery<ServiceQuery>()
          .registryName("deep-fryer")
          .variant("deep-fried")
          .uniqueResult(session)

        val run = service?.let {
          queryFactory.newQuery<BackfillRunQuery>()
            .serviceId(it.id)
            .uniqueResult(session)
        }

        assertNotNull(run)
        assertThat(run.state).isEqualTo(BackfillState.PAUSED)
        assertThat(run.created_by_user).isEqualTo("molly")
        assertThat(run.approved_by_user).isNull()
        assertThat(run.approved_at).isNull()
        assertThat(run.service.variant).isEqualTo("deep-fried")
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
    }
  }

  @Test
  fun prepareWithExtraParameters() {
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

    fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
      PrepareBackfillResponse.Builder()
        .partitions(
          listOf(
            PrepareBackfillResponse.Partition.Builder()
              .partition_name("only")
              .backfill_range(KeyRange("0".encodeUtf8(), "1000".encodeUtf8()))
              .build(),
          ),
        )
        .parameters(mapOf("idempotence_token" to "filaback".encodeUtf8()))
        .build(),
    )

    scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      )

      transacter.transaction { session ->
        val run = queryFactory.newQuery<BackfillRunQuery>().uniqueResult(session)
        assertNotNull(run)
        assertThat(run.parameters()).isEqualTo(
          mapOf("idempotence_token" to "filaback".encodeUtf8()),
        )
      }
    }
  }

  @Test
  fun prepareWithExtraParametersOverride() {
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

    fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
      PrepareBackfillResponse.Builder()
        .partitions(
          listOf(
            PrepareBackfillResponse.Partition.Builder()
              .partition_name("only")
              .backfill_range(KeyRange("0".encodeUtf8(), "1000".encodeUtf8()))
              .build(),
          ),
        )
        .parameters(mapOf("idempotence_token" to "filaback".encodeUtf8()))
        .build(),
    )

    scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .parameter_map(mapOf("idempotence_token" to "ketchup".encodeUtf8()))
          .build(),
      )

      transacter.transaction { session ->
        val run = queryFactory.newQuery<BackfillRunQuery>().uniqueResult(session)
        assertNotNull(run)
        assertThat(run.parameters()).isEqualTo(
          mapOf("idempotence_token" to "filaback".encodeUtf8()),
        )
      }
    }
  }

  @Test
  fun `when prepare returns error message it gets propagated to the user`() {
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

    fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
      PrepareBackfillResponse.Builder()
        .error_message("We're out of chicken")
        .build(),
    )

    scope.fakeCaller(user = "molly") {
      assertThatThrownBy {
        createBackfillAction.create(
          "deep-fryer",
          RESERVED_VARIANT,
          CreateBackfillRequest.Builder()
            .backfill_name("ChickenSandwich")
            .parameter_map(mapOf("idempotence_token" to "ketchup".encodeUtf8()))
            .build(),
        )
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessage("PrepareBackfill on `deep-fryer` failed: We're out of chicken. connectionData: FakeBackfilaClientServiceClient so no connection")

      transacter.transaction { session ->
        val runs = queryFactory.newQuery<BackfillRunQuery>().list(session)
        assertThat(runs).isEmpty()
      }
    }
  }

  @Test
  fun `error is shown when parameter is too long`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData.Builder()
                .name("ChickenSandwich")
                .parameters(listOf(Parameter.Builder().name("sauce").build()))
                .build(),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .build(),
      )
    }
    scope.fakeCaller(user = "molly") {
      assertThatThrownBy {
        createBackfillAction.create(
          "deep-fryer",
          RESERVED_VARIANT,
          CreateBackfillRequest.Builder()
            .backfill_name("ChickenSandwich")
            .parameter_map(mapOf("sauce" to "a".repeat(BackfillCreator.MAX_PARAMETER_VALUE_SIZE + 1).encodeUtf8()))
            .build(),
        )
      }.isInstanceOf(BadRequestException::class.java)
        .hasMessage("parameter sauce is too long (max 1000 characters)")

      transacter.transaction { session ->
        val runs = queryFactory.newQuery<BackfillRunQuery>().list(session)
        assertThat(runs).isEmpty()
      }
    }
  }
}
