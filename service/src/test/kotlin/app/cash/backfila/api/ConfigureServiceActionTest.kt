package app.cash.backfila.api

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.client.Connectors.ENVOY
import app.cash.backfila.client.Connectors.HTTP
import app.cash.backfila.dashboard.GetRegisteredBackfillsAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.Parameter
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import com.google.inject.Module
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import javax.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class ConfigureServiceActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var clock: Clock

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var getRegisteredBackfillsAction: GetRegisteredBackfillsAction

  @Inject
  @BackfilaDb
  lateinit var transacter: Transacter

  @Inject
  lateinit var queryFactory: Query.Factory

  @Inject
  lateinit var scope: ActionScope

  @Test
  fun changeServiceConnector() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .connector_type(ENVOY)
          .connector_extra_data("{\"clusterType\":\"production\"}")
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).isEmpty()

      transacter.transaction { session ->
        val dbService = queryFactory.newQuery<ServiceQuery>()
          .registryName("deep-fryer")
          .uniqueResult(session)!!
        assertThat(dbService.connector).isEqualTo(ENVOY)
        assertThat(dbService.connector_extra_data).isEqualTo("{\"clusterType\":\"production\"}")
      }

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .connector_type(HTTP)
          .connector_extra_data("{\"clusterType\":\"staging\", \"headers\": [{\"name\": \"foo\", \"value\": \"bar\"}]}")
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).isEmpty()

      transacter.transaction { session ->
        val dbService = queryFactory.newQuery<ServiceQuery>()
          .registryName("deep-fryer")
          .uniqueResult(session)!!
        assertThat(dbService.connector).isEqualTo(HTTP)
        assertThat(dbService.connector_extra_data).isEqualTo("{\"clusterType\":\"staging\", \"headers\": [{\"name\": \"foo\", \"value\": \"bar\"}]}")
      }
    }
  }

  @Test
  fun configureServiceSyncsBackfills() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                null, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "zzz", "Description", listOf(), null, null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).containsOnly("zzz")
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")
    }
  }

  @Test
  fun deleteOneOfTwo() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                false, null,
              ),
              ConfigureServiceRequest.BackfillData(
                "zzz", "Description", listOf(), null, null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz", "zzz")
      assertThat(deletedBackfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "zzz", "Description", listOf(), null, null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).containsOnly("zzz")
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")
    }
  }

  @Test
  fun reintroduceBackfill() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).isEmpty()
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      // The reintroduced backfill is created, and the old one kept around.
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")
    }
  }

  @Test
  fun modifyBackfillRequiresApprovalBackAndForth() {
    scope.fakeCaller(service = "deep-fryer") {
      // Going back and forth between approval required creates new registered backfills.
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                true, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = null,
          parameters = listOf(), requires_approval = false, delete_by = null,
        ),
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = null,
          parameters = listOf(), requires_approval = true, delete_by = null,
        ),
        Backfill(
          "xyz", null, type_provided = null, type_consumed = null,
          parameters = listOf(), requires_approval = false, delete_by = null,
        ),
      )
    }
  }

  @Test
  fun modifyBackfillDeleteBy() {
    scope.fakeCaller(service = "deep-fryer") {
      // Going back and forth between approval required creates new registered backfills.
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).isEmpty()

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                false, clock.instant().plus(1L, DAYS).toEpochMilli(),
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      assertThat(backfillNames("deep-fryer")).containsOnly("xyz")
      assertThat(deletedBackfillNames("deep-fryer")).containsOnly("xyz")

      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = null,
          parameters = listOf(), requires_approval = false, delete_by = null,
        ),
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = null,
          parameters = listOf(), requires_approval = false, delete_by = clock.instant().plus(1L, DAYS),
        ),
        Backfill(
          "xyz", null, type_provided = null, type_consumed = null,
          parameters = listOf(), requires_approval = false, delete_by = null,
        ),
      )
    }
  }

  @Test
  fun modifyParams() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description",
                listOf(Parameter.Builder().name("abc").build()),
                null, null, false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description",
                listOf(
                  Parameter.Builder().name("abc").build(),
                  Parameter.Builder().name("def").build(),
                ),
                null, null, false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      // Repeat the final one twice, no changes shouldn't create a new backfill.
      repeat(2) {
        configureServiceAction.configureService(
          ConfigureServiceRequest.Builder()
            .backfills(
              listOf(
                ConfigureServiceRequest.BackfillData(
                  "xyz", "Description",
                  listOf(
                    Parameter.Builder().name("def").build(),
                    Parameter.Builder().name("xyz").build(),
                  ),
                  null, null, false, null,
                ),
              ),
            )
            .connector_type(ENVOY)
            .build(),
        )
      }
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = null,
          parameters = listOf(), requires_approval = false, delete_by = null,
        ),
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = null,
          parameters = listOf(
            Parameter.Builder().name("abc").required(false).build(),
          ),
          requires_approval = false, delete_by = null,
        ),
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = null,
          parameters = listOf(
            Parameter.Builder().name("abc").required(false).build(),
            Parameter.Builder().name("def").required(false).build(),
          ),
          requires_approval = false, delete_by = null,
        ),
        Backfill(
          "xyz", null, type_provided = null, type_consumed = null,
          parameters = listOf(
            Parameter.Builder().name("def").required(false).build(),
            Parameter.Builder().name("xyz").required(false).build(),
          ),
          requires_approval = false, delete_by = null,
        ),
      )
    }
  }

  @Test
  fun `change param description`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description",
                listOf(Parameter.Builder().name("abc").build()),
                null, null, false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      // Add description
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description",
                listOf(Parameter.Builder().name("abc").description("desc").build()),
                null, null, false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      // Change description
      // Repeat the final one twice, no changes shouldn't create a new backfill.
      repeat(2) {
        configureServiceAction.configureService(
          ConfigureServiceRequest.Builder()
            .backfills(
              listOf(
                ConfigureServiceRequest.BackfillData(
                  "xyz", "Description",
                  listOf(Parameter.Builder().name("abc").description("new desc").build()),
                  null, null, false, null,
                ),
              ),
            )
            .connector_type(ENVOY)
            .build(),
        )
      }
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = null,
          parameters = listOf(
            Parameter.Builder().name("abc").required(false).build(),
          ),
          requires_approval = false, delete_by = null,
        ),
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = null,
          parameters = listOf(
            Parameter.Builder().name("abc").required(false).description("desc").build(),
          ),
          requires_approval = false, delete_by = null,
        ),
        Backfill(
          "xyz", null, type_provided = null, type_consumed = null,
          parameters = listOf(
            Parameter.Builder().name("abc").required(false).description("new desc").build(),
          ),
          requires_approval = false, delete_by = null,
        ),
      )
    }
  }

  @Test
  fun `change param required`() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description",
                listOf(Parameter.Builder().name("abc").required(false).build()),
                null, null, false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      // Make required
      // Repeat the final one twice, no changes shouldn't create a new backfill.
      repeat(2) {
        configureServiceAction.configureService(
          ConfigureServiceRequest.Builder()
            .backfills(
              listOf(
                ConfigureServiceRequest.BackfillData(
                  "xyz", "Description",
                  listOf(Parameter.Builder().name("abc").required(true).build()),
                  null, null, false, null,
                ),
              ),
            )
            .connector_type(ENVOY)
            .build(),
        )
      }
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = null,
          parameters = listOf(
            Parameter.Builder().name("abc").required(false).build(),
          ),
          requires_approval = false, delete_by = null,
        ),
        Backfill(
          "xyz", null, type_provided = null, type_consumed = null,
          parameters = listOf(
            Parameter.Builder().name("abc").required(true).build(),
          ),
          requires_approval = false, delete_by = null,
        ),
      )
    }
  }

  @Test
  fun modifyTypeProvided() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), "String", null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), "Int", null,
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
        Backfill(
          "xyz", clock.instant(), type_provided = "String", type_consumed = null,
          parameters = listOf(), requires_approval = false, delete_by = null,
        ),
        Backfill(
          "xyz", null, type_provided = "Int", type_consumed = null,
          parameters = listOf(), requires_approval = false, delete_by = null,
        ),
      )
    }
  }

  @Test
  fun modifyTypeConsumed() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, "String",
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, "Int",
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
        Backfill(
          "xyz", clock.instant(), type_provided = null, type_consumed = "String",
          parameters = listOf(), requires_approval = false, delete_by = null,
        ),
        Backfill(
          "xyz", null, type_provided = null, type_consumed = "Int",
          parameters = listOf(), requires_approval = false, delete_by = null,
        ),
      )
    }
  }

  @Test
  fun unchangedBackfillNotDuplicated() {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, "String",
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "xyz", "Description", listOf(), null, "String",
                false, null,
              ),
            ),
          )
          .connector_type(ENVOY)
          .build(),
      )
      val backfills = backfills("deep-fryer")
      assertThat(backfills).containsOnly(
        Backfill(
          "xyz", deleted_in_service_at = null, type_provided = null,
          type_consumed = "String", parameters = listOf(), requires_approval = false, delete_by = null,
        ),
      )
    }
  }

  @Test
  fun invalidConnectorExtraData() {
    scope.fakeCaller(service = "deep-fryer") {
      assertThatThrownBy {
        configureServiceAction.configureService(
          ConfigureServiceRequest.Builder()
            .backfills(
              listOf(
                ConfigureServiceRequest.BackfillData(
                  "xyz", "Description", listOf(), null, "String",
                  false, null,
                ),
              ),
            )
            .connector_type(ENVOY)
            .connector_extra_data("poop")
            .build(),
        )
      }.isInstanceOf(JsonEncodingException::class.java)
    }

    scope.fakeCaller(service = "deep-fryer") {
      assertThatThrownBy {
        configureServiceAction.configureService(
          ConfigureServiceRequest.Builder()
            .backfills(
              listOf(
                ConfigureServiceRequest.BackfillData(
                  "xyz", "Description", listOf(), null, "String",
                  false, null,
                ),
              ),
            )
            .connector_type(ENVOY)
            .connector_extra_data("{\"clusterType\":\"production\", \"headers\": [{\"name\": \"foo\"}]}")
            .build(),
        )
      }.isInstanceOf(JsonDataException::class.java)
    }

    scope.fakeCaller(service = "deep-fryer") {
      assertThatThrownBy {
        configureServiceAction.configureService(
          ConfigureServiceRequest.Builder()
            .backfills(
              listOf(
                ConfigureServiceRequest.BackfillData(
                  "xyz", "Description", listOf(), null, "String",
                  false, null,
                ),
              ),
            )
            .connector_type(ENVOY)
            .connector_extra_data("{\"clusterType\":\"production\", \"headers\": [{\"value\": \"bar\"}]}")
            .build(),
        )
      }.isInstanceOf(JsonDataException::class.java)
    }

    scope.fakeCaller(service = "deep-fryer") {
      val headers = mutableListOf<String>()
      for (i in 0..5000) {
        headers.add("{\"name\": \"$i\", \"value\": \"$i\"}")
      }

      assertThatThrownBy {
        configureServiceAction.configureService(
          ConfigureServiceRequest.Builder()
            .backfills(
              listOf(
                ConfigureServiceRequest.BackfillData(
                  "xyz", "Description", listOf(), null, "String",
                  false, null,
                ),
              ),
            )
            .connector_type(ENVOY)
            .connector_extra_data("{\"clusterType\":\"production\", \"headers\": [${headers.joinToString()}]}")
            .build(),
        )
      }.isInstanceOf(IllegalStateException::class.java)
    }

    scope.fakeCaller(service = "deep-fryer") {
      val headers = mutableListOf<String>()
      val name = "a".repeat(5000)
      val value = "b".repeat(5000)
      headers.add("{\"name\": \"$name\", \"value\": \"$value\"}")

      assertThatThrownBy {
        configureServiceAction.configureService(
          ConfigureServiceRequest.Builder()
            .backfills(
              listOf(
                ConfigureServiceRequest.BackfillData(
                  "xyz", "Description", listOf(), null, "String",
                  false, null,
                ),
              ),
            )
            .connector_type(ENVOY)
            .connector_extra_data("{\"clusterType\":\"production\", \"headers\": [${headers.joinToString()}]}")
            .build(),
        )
      }.isInstanceOf(IllegalStateException::class.java)
    }
  }

  private fun backfillNames(serviceName: String): List<String> {
    return getRegisteredBackfillsAction.backfills(serviceName)
      .backfills.map { it.name }
  }

  private fun deletedBackfillNames(serviceName: String): List<String> {
    return transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
        .registryName(serviceName)
        .uniqueResult(session)!!
      queryFactory.newQuery<RegisteredBackfillQuery>()
        .serviceId(dbService.id)
        .notActive()
        .list(session)
        .map { it.name }
    }
  }

  data class Backfill(
    val name: String,
    val deleted_in_service_at: Instant?,
    val type_provided: String?,
    val type_consumed: String?,
    val parameters: List<Parameter>,
    val requires_approval: Boolean,
    val delete_by: Instant?,
  )

  private fun backfills(serviceName: String): List<Backfill> {
    return transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
        .registryName(serviceName)
        .uniqueResult(session)!!
      queryFactory.newQuery<RegisteredBackfillQuery>()
        .serviceId(dbService.id)
        .list(session)
        .map {
          assertThat(it.parameterNames()).isEqualTo(it.parameters.map { it.name })
          Backfill(
            it.name,
            it.deleted_in_service_at,
            it.type_provided,
            it.type_consumed,
            it.parameters.map { p -> Parameter(p.name, p.description, p.required) },
            it.requires_approval,
            it.delete_by,
          )
        }
    }
  }
}
