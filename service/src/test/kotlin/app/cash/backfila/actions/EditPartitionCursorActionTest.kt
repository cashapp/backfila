package app.cash.backfila.actions

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.FakeBackfilaCallbackConnector
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.dashboard.EditPartitionCursorAction
import app.cash.backfila.dashboard.EditPartitionCursorHandlerAction
import app.cash.backfila.dashboard.GetBackfillStatusAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbRunPartition
import app.cash.backfila.service.persistence.RunPartitionQuery
import com.google.inject.Module
import jakarta.inject.Inject
import javax.servlet.http.HttpServletRequest
import misk.Action
import misk.MiskCaller
import misk.api.HttpRequest
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.inject.toKey
import misk.scope.ActionScope
import misk.scope.ActionScopedProviderModule
import misk.security.authz.MiskCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.web.FakeHttpCall
import misk.web.HttpCall
import misk.web.dashboard.BaseDashboardModule
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@MiskTest(startService = true)
class EditPartitionCursorActionTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = TestModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var createBackfillAction: CreateBackfillAction

  @Inject
  lateinit var getBackfillStatusAction: GetBackfillStatusAction

  @Inject
  lateinit var editPartitionCursorAction: EditPartitionCursorAction

  @Inject
  lateinit var editPartitionCursorHandlerAction: EditPartitionCursorHandlerAction

  @Inject
  lateinit var fakeBackfilaClientServiceClient: FakeBackfilaCallbackConnector

  @Inject
  lateinit var scope: ActionScope

  @Inject
  lateinit var queryFactory: Query.Factory

  @Inject
  @BackfilaDb
  lateinit var transacter: Transacter

  @Inject
  lateinit var clock: FakeClock

  @Test
  fun `paths never interpolate the partition name`() {
    assertThat(EditPartitionCursorAction.path(24852L, 77568L))
      .isEqualTo("/backfills/24852/partitions/77568/edit-cursor")
    assertThat(EditPartitionCursorHandlerAction.path(24852L, 77568L))
      .isEqualTo("/api/backfill/24852/partitions/77568/cursor")
  }

  @Test
  fun `form page renders for a partition whose name is not url safe`() {
    val id = createPausedBackfill("region-shard-us-east#orders_local_ro_0")

    inScope {
      val partitionId = onlyPartitionId(id)

      assertThat(editPartitionCursorAction.get(id, partitionId).statusCode).isEqualTo(200)

      assertThatThrownBy { editPartitionCursorAction.get(id, partitionId + 1000) }
        .isInstanceOf(BadRequestException::class.java)
    }
  }

  @Test
  fun `form leaves the new range end blank and submits its current snapshot`() {
    val id = createPausedBackfill("only")

    inScope {
      val response = editPartitionCursorAction.get(id, onlyPartitionId(id))
      val html = Buffer().also { response.body.writeTo(it) }.readUtf8()
      val endInput = Regex("""<input\b[^>]*\bname="new_range_end"[^>]*>""").find(html)!!.value
      val endValue = Regex("""\bvalue="([^"]*)"""").find(endInput)?.groupValues?.get(1)
      val snapshotInput = Regex("""<input\b[^>]*\bname="range_end_snapshot"[^>]*>""").find(html)!!.value

      assertThat(endValue.orEmpty()).isEmpty()
      assertThat(endInput).contains("""placeholder="17701296"""")
      assertThat(snapshotInput).contains("""type="hidden"""", """value="17701296"""")
    }
  }

  @Test
  fun `edits a partition whose name is not url safe`() {
    val id = createPausedBackfill("region-shard-us-east#orders_local_ro_0")

    inScope {
      val partitionId = onlyPartitionId(id)
      assertThat(cursorOf(id)).isNull()

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", "1079486")

      assertThat(response.statusCode).isEqualTo(303)
      assertThat(cursorOf(id)).isEqualTo("1079486")
    }
  }

  @Test
  fun `edits a partition using the matching non null snapshot the form submits`() {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      editPartitionCursorHandlerAction.get(id, partitionId, "", "500")

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "500", "9000")

      assertThat(response.statusCode).isEqualTo(303)
      assertThat(cursorOf(id)).isEqualTo("9000")
    }
  }

  @Test
  fun `rejects a stale snapshot`() {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      editPartitionCursorHandlerAction.get(id, partitionId, "", "500")

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "499", "9000")

      assertThat(response.statusCode).isEqualTo(200)
      assertThat(cursorOf(id)).isEqualTo("500")
    }
  }

  @Test
  fun `a missing new cursor does not clear the cursor`() {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      editPartitionCursorHandlerAction.get(id, partitionId, "", "500")

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "500", null)

      assertThat(response.statusCode).isEqualTo(200)
      assertThat(cursorOf(id)).isEqualTo("500")
    }
  }

  @Test
  fun `narrows the range end without touching the cursor`() {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      editPartitionCursorHandlerAction.get(id, partitionId, "", "500")

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "500", null, "9000", "17701296")

      assertThat(response.statusCode).isEqualTo(303)
      assertThat(cursorOf(id)).isEqualTo("500")
      assertThat(rangeEndOf(id)).isEqualTo("9000")
    }
  }

  @Test
  fun `edits the cursor and the range end together`() {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", "500", "9000", "17701296")

      assertThat(response.statusCode).isEqualTo(303)
      assertThat(cursorOf(id)).isEqualTo("500")
      assertThat(rangeEndOf(id)).isEqualTo("9000")
    }
  }

  @Test
  fun `a stale snapshot leaves the range end untouched`() {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      editPartitionCursorHandlerAction.get(id, partitionId, "", "500")

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "499", null, "9000", "17701296")

      assertThat(response.statusCode).isEqualTo(200)
      assertThat(rangeEndOf(id)).isEqualTo("17701296")
    }
  }

  @Test
  fun `rejects a stale range end snapshot even when the cursor has not changed`() {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      val firstResponse = editPartitionCursorHandlerAction.get(id, partitionId, "", null, "9000", "17701296")
      assertThat(firstResponse.statusCode).isEqualTo(303)

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", "500", "8000", "17701296")

      assertThat(response.statusCode).isEqualTo(200)
      assertThat(cursorOf(id)).isNull()
      assertThat(rangeEndOf(id)).isEqualTo("9000")
    }
  }

  @Test
  fun `a stale cursor only form preserves a newer range end`() {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      val firstResponse = editPartitionCursorHandlerAction.get(id, partitionId, "", null, "9000", "17701296")
      assertThat(firstResponse.statusCode).isEqualTo(303)

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", "500", "", "17701296")

      assertThat(response.statusCode).isEqualTo(303)
      assertThat(cursorOf(id)).isEqualTo("500")
      assertThat(rangeEndOf(id)).isEqualTo("9000")
    }
  }

  @Test
  fun `cursor only edits preserve a binary range end`() {
    val id = createPausedBackfill("only")
    val binaryEnd = ByteString.of(0xFF.toByte(), 0xFE.toByte())

    inScope {
      val partitionId = onlyPartitionId(id)
      withPartition(partitionId) { it.pkey_range_end = binaryEnd }

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", "500", "", binaryEnd.utf8())

      assertThat(response.statusCode).isEqualTo(303)
      assertThat(cursorOf(id)).isEqualTo("500")
      assertThat(withPartition(partitionId) { it.pkey_range_end }).isEqualTo(binaryEnd)
    }
  }

  @Test
  fun `rejects a lossy binary range end without applying the cursor edit`() {
    val id = createPausedBackfill("only")
    val binaryEnd = ByteString.of(0xFF.toByte(), 0xFE.toByte())

    inScope {
      val partitionId = onlyPartitionId(id)
      withPartition(partitionId) { it.pkey_range_end = binaryEnd }

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", "500", binaryEnd.utf8(), binaryEnd.utf8())

      assertThat(response.statusCode).isEqualTo(200)
      assertThat(cursorOf(id)).isNull()
      assertThat(withPartition(partitionId) { it.pkey_range_end }).isEqualTo(binaryEnd)
    }
  }

  @Test
  fun `rejects a range end for an unbounded partition`() {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      withPartition(partitionId) { it.pkey_range_end = null }

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", "500", "9000", "")

      assertThat(response.statusCode).isEqualTo(200)
      assertThat(cursorOf(id)).isNull()
      assertThat(rangeEndOf(id)).isNull()
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `changing the range end restarts precomputing without discarding backfilled counts`(precomputingDone: Boolean) {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      withPartition(partitionId) {
        it.precomputing_done = precomputingDone
        it.precomputing_pkey_cursor = "10000".encodeUtf8()
        it.computed_scanned_record_count = 10000
        it.computed_matching_record_count = 2500
        it.backfilled_scanned_record_count = 500
        it.backfilled_matching_record_count = 125
      }

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", null, "9000", "17701296")

      assertThat(response.statusCode).isEqualTo(303)
      val partition = getBackfillStatusAction.status(id).partitions.single()
      assertThat(partition.precomputing_done).isFalse()
      assertThat(partition.precomputing_pkey_cursor).isNull()
      assertThat(partition.computed_scanned_record_count).isZero()
      assertThat(partition.computed_matching_record_count).isZero()
      assertThat(partition.backfilled_scanned_record_count).isEqualTo(500)
      assertThat(partition.backfilled_matching_record_count).isEqualTo(125)
    }
  }

  @Test
  fun `submitting an unchanged range end preserves precomputed progress`() {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      withPartition(partitionId) {
        it.precomputing_done = true
        it.precomputing_pkey_cursor = "17701296".encodeUtf8()
        it.computed_scanned_record_count = 10000
        it.computed_matching_record_count = 2500
      }

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", "500", "17701296", "17701296")

      assertThat(response.statusCode).isEqualTo(303)
      assertThat(cursorOf(id)).isEqualTo("500")
      val partition = getBackfillStatusAction.status(id).partitions.single()
      assertThat(partition.precomputing_done).isTrue()
      assertThat(partition.precomputing_pkey_cursor).isEqualTo("17701296")
      assertThat(partition.computed_scanned_record_count).isEqualTo(10000)
      assertThat(partition.computed_matching_record_count).isEqualTo(2500)
    }
  }

  @ParameterizedTest
  @ValueSource(longs = [-60, 60])
  fun `rejects range changes while a paused partition still has a lease token`(expiryOffsetSeconds: Long) {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      withPartition(partitionId) {
        it.lease_token = "runner-still-stopping"
        it.lease_expires_at = clock.instant().plusSeconds(expiryOffsetSeconds)
        it.precomputing_done = true
        it.precomputing_pkey_cursor = "17701296".encodeUtf8()
        it.computed_scanned_record_count = 10000
      }

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", "500", "9000", "17701296")

      assertThat(response.statusCode).isEqualTo(200)
      assertThat(cursorOf(id)).isNull()
      assertThat(rangeEndOf(id)).isEqualTo("17701296")
      val partition = getBackfillStatusAction.status(id).partitions.single()
      assertThat(partition.precomputing_done).isTrue()
      assertThat(partition.precomputing_pkey_cursor).isEqualTo("17701296")
      assertThat(partition.computed_scanned_record_count).isEqualTo(10000)
    }
  }

  @ParameterizedTest
  @EnumSource(value = BackfillState::class, names = ["RUNNING", "COMPLETE"])
  fun `rejects range changes for a partition that is not paused`(partitionState: BackfillState) {
    val id = createPausedBackfill("only")

    inScope {
      val partitionId = onlyPartitionId(id)
      withPartition(partitionId) { it.run_state = partitionState }

      val response = editPartitionCursorHandlerAction.get(id, partitionId, "", "500", "9000", "17701296")

      assertThat(response.statusCode).isEqualTo(200)
      assertThat(cursorOf(id)).isNull()
      assertThat(rangeEndOf(id)).isEqualTo("17701296")
    }
  }

  @Test
  fun `refuses a partition belonging to a different backfill`() {
    val otherId = createPausedBackfill("only")
    val id = createPausedBackfill("only")

    inScope {
      val response = editPartitionCursorHandlerAction.get(id, onlyPartitionId(otherId), "", "500")

      assertThat(response.statusCode).isEqualTo(200)
      assertThat(cursorOf(otherId)).isNull()
      assertThat(cursorOf(id)).isNull()
    }
  }

  @Test
  fun `leaves a cursor that is not utf8 untouched`() {
    val id = createPausedBackfill("only")
    val binaryCursor = ByteString.of(0xFF.toByte(), 0xFE.toByte())

    inScope {
      val partitionId = onlyPartitionId(id)
      setCursorBytes(partitionId, binaryCursor)

      val response = editPartitionCursorHandlerAction.get(id, partitionId, cursorOf(id), "500")

      assertThat(response.statusCode).isEqualTo(200)
      assertThat(cursorBytesOf(partitionId)).isEqualTo(binaryCursor)
    }
  }

  private fun onlyPartitionId(id: Long) = getBackfillStatusAction.status(id).partitions.single().id

  private fun cursorOf(id: Long) = getBackfillStatusAction.status(id).partitions.single().pkey_cursor

  private fun rangeEndOf(id: Long) =
    getBackfillStatusAction.status(id).partitions.single().pkey_end

  private fun cursorBytesOf(partitionId: Long) = transacter.transaction { session ->
    queryFactory.newQuery<RunPartitionQuery>()
      .partitionId(Id(partitionId))
      .uniqueResult(session)!!
      .pkey_cursor
  }

  private fun setCursorBytes(partitionId: Long, cursor: ByteString) {
    transacter.transaction { session ->
      queryFactory.newQuery<RunPartitionQuery>()
        .partitionId(Id(partitionId))
        .uniqueResult(session)!!
        .pkey_cursor = cursor
    }
  }

  private fun <T> withPartition(partitionId: Long, block: (DbRunPartition) -> T): T =
    transacter.transaction { session ->
      val partition = queryFactory.newQuery<RunPartitionQuery>()
        .partitionId(Id(partitionId))
        .uniqueResult(session)!!
      block(partition)
    }

  private fun createPausedBackfill(partitionName: String): Long {
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

    fakeBackfilaClientServiceClient.prepareBackfillResponses.add(
      PrepareBackfillResponse.Builder()
        .partitions(
          listOf(
            PrepareBackfillResponse.Partition.Builder()
              .partition_name(partitionName)
              .backfill_range(KeyRange("1".encodeUtf8(), "17701296".encodeUtf8()))
              .build(),
          ),
        )
        .build(),
    )

    return scope.fakeCaller(user = "molly") {
      createBackfillAction.create(
        "deep-fryer",
        RESERVED_VARIANT,
        CreateBackfillRequest.Builder()
          .backfill_name("ChickenSandwich")
          .build(),
      ).backfill_run_id
    }
  }

  private fun <T> inScope(function: () -> T): T = scope.create(
    mapOf(
      keyOf<MiskCaller>() to MiskCaller(user = "molly"),
      HttpCall::class.toKey() to FakeHttpCall(
        url = "http://backfila/api/backfill/1/partitions/1/cursor".toHttpUrl(),
      ),
    ),
  ).inScope(function)

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(BackfilaTestingModule())
      install(BaseDashboardModule(true))

      install(
        object : ActionScopedProviderModule() {
          override fun configureProviders() {
            bindSeedData(HttpCall::class)
            bindSeedData(HttpRequest::class)
            bindSeedData(HttpServletRequest::class)
            bindSeedData(Action::class)
            newMultibinder<MiskCallerAuthenticator>()
          }
        },
      )
    }
  }
}
