package app.cash.backfila.client.misk.spanner

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createDryRun
import app.cash.backfila.embedded.createWetRun
import com.google.cloud.spanner.DatabaseClient
import com.google.cloud.spanner.DatabaseId
import com.google.cloud.spanner.InstanceId
import com.google.cloud.spanner.KeyRange
import com.google.cloud.spanner.KeySet
import com.google.cloud.spanner.Mutation
import com.google.cloud.spanner.Mutation.WriteBuilder
import com.google.cloud.spanner.Spanner
import com.google.cloud.spanner.Statement
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalArgumentException
import java.util.UUID
import javax.inject.Inject

@MiskTest(startService = true)
class SpannerBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module = TestingModule()

  @Inject
  lateinit var backfila: Backfila
  @Inject lateinit var spanner: Spanner
  private lateinit var dbClient: DatabaseClient

  @BeforeEach
  fun setup() {
    dbClient = createDbClient(
      spanner, project = TestingModule.PROJECT_ID, instance = TestingModule.INSTANCE_ID, db = TestingModule.DB_ID,
    )
    val adminDbClient = spanner.databaseAdminClient
    val adminClient = adminDbClient.getDatabase(TestingModule.INSTANCE_ID, TestingModule.DB_ID)
    val columns = TrackData.COLUMNS.joinToString(",") {
      "${it.name} STRING(128)"
    }

    val statement = "CREATE TABLE ${TrackData.TABLE_NAME}($columns) PRIMARY KEY (id)"
    adminClient.updateDdl(listOf(statement), null).get()

    dbClient.readWriteTransaction().run {
      repeat(10) { n ->
        val uuid = UUID.randomUUID().toString()
        val mutation = Mutation.newInsertBuilder(TrackData.TABLE_NAME)
          .setColumnTo(TrackData.Column.id.name, uuid)
          .setColumnTo(TrackData.Column.album_title.name, "album title $n")
          .setColumnTo(TrackData.Column.album_token.name, "token $n")
          .setColumnTo(TrackData.Column.track_title.name, "track title $n")
          .setColumnTo(TrackData.Column.artist_name.name, "artist $n")
          .build()
        it.buffer(mutation)
      }
    }
  }

  @AfterEach
  fun cleanup() {
    val tableNameQuery = dbClient
      .singleUseReadOnlyTransaction()
      .executeQuery(
        Statement.of(
          """
          SELECT
            table_name
          FROM
            information_schema.tables
          WHERE
            table_catalog = '' and table_schema = ''
          """.trimIndent()
        )
      )

    val tableNames: MutableList<String> = mutableListOf()
    while (tableNameQuery.next()) {
      tableNames.add(tableNameQuery.getString(0))
    }

    if (tableNames.size == 0) return
    dbClient.readWriteTransaction().run {
      it.batchUpdate(
        tableNames.map {
          tableName ->
          Statement.of("DELETE FROM $tableName WHERE true")
        }
      )
    }

    val adminDbClient = spanner.databaseAdminClient
    val adminClient = adminDbClient.getDatabase(TestingModule.INSTANCE_ID, TestingModule.DB_ID)
    val statement = "DROP TABLE ${TrackData.TABLE_NAME}"
    adminClient.updateDdl(listOf(statement), null).get()
  }

  @Test
  fun `happy path`() {
    val run = backfila.createWetRun<MakeTracksExplicitBackfill>(Param())
    run.execute()

    verify(isExplicit = true)
  }

  @Test
  fun `happy path dry run`() {
    val run = backfila.createDryRun<MakeTracksExplicitBackfill>(Param())
    run.execute()

    verify(isExplicit = false)
  }

  @Test
  fun `validation stops wet run`() {
    assertThrows<IllegalArgumentException> {
      backfila.createWetRun<MakeTracksExplicitBackfill>(Param(failValidation = true))
    }
  }

  @Test
  fun `small scan size scans everything`() {
    val run = backfila.createWetRun<MakeTracksExplicitBackfill>(Param())
    run.scanSize = 2

    run.precomputeRemaining()
    assertThat(run.precomputeScannedCount).isEqualTo(10)
    assertThat(run.precomputeMatchingCount).isEqualTo(10)
    run.scanRemaining()
    assertThat(run.batchesToRunSnapshot.size).isEqualTo(5)
    run.runAllScanned()
    assertThat(run.complete()).isTrue

    verify(isExplicit = true)
  }

  @Test
  fun `small batch size processes everything`() {
    val run = backfila.createWetRun<MakeTracksExplicitBackfill>(Param())
    run.batchSize = 2

    run.precomputeRemaining()
    assertThat(run.precomputeScannedCount).isEqualTo(10)
    assertThat(run.precomputeMatchingCount).isEqualTo(10)
    run.scanRemaining()
    assertThat(run.batchesToRunSnapshot.size).isEqualTo(5)
    run.runAllScanned()
    assertThat(run.complete()).isTrue

    verify(isExplicit = true)
  }

  private fun verify(isExplicit: Boolean) {
    dbClient.singleUseReadOnlyTransaction().run {
      val result = this.read(
        TrackData.TABLE_NAME,
        KeySet.all(),
        listOf(TrackData.Column.track_title.name)
      )
      result.use {
        while (result.next()) {
          val isExplicitResult = result.getString(TrackData.Column.track_title.name).endsWith("(EXPLICIT)")
          assertThat(isExplicit).isEqualTo(isExplicitResult)
        }
      }
    }
  }

  data class Param(
    val failValidation: Boolean = false,
  )

  class MakeTracksExplicitBackfill @Inject constructor(
    spanner: Spanner,
  ) : SpannerBackfill<Param>() {
    override val dbClient: DatabaseClient = createDbClient(
      spanner, project = TestingModule.PROJECT_ID, instance = TestingModule.INSTANCE_ID, db = TestingModule.DB_ID
    )
    override val primaryKeyColumns: List<String> = listOf(TrackData.Column.id.name)
    override val tableName: String = TrackData.TABLE_NAME

    override fun runBatch(range: KeyRange, config: BackfillConfig<Param>) {
      val transaction = dbClient.readWriteTransaction()
      transaction.run { transaction ->
        val result = transaction.read(
          tableName,
          KeySet.range(range),
          listOf(TrackData.Column.id.name, TrackData.Column.track_title.name),
        )
        result.use {
          while (result.next()) {
            val title = result.getString(TrackData.Column.track_title.name)
            if (!title.endsWith("(EXPLICIT)") && !config.dryRun) {
              val id = result.getString(TrackData.Column.id.name)
              val mutation = Mutation.newUpdateBuilder(tableName)
              val change = mutation.setColumnTo(TrackData.Column.id.name, id)
                .setColumnTo(TrackData.Column.track_title.name, "$title (EXPLICIT)")
                .build()
              transaction.buffer(change)
            }
          }
        }
      }
    }

    override fun validate(config: BackfillConfig<Param>) {
      super.validate(config)
      require(!config.parameters.failValidation)
    }
  }
}

fun WriteBuilder.setColumnTo(column: String, value: String): WriteBuilder = set(column).to(value)

fun createDbClient(spanner: Spanner, project: String, instance: String, db: String) =
  spanner.getDatabaseClient(DatabaseId.of(InstanceId.of(project, instance), db))
