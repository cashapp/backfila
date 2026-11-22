package app.cash.backfila.service.persistence

import java.io.File
import kotlin.test.fail
import org.junit.jupiter.api.Test

/**
 * The declarative files in `src/main/resources/mysql/schema/backfila/` describe exactly the schema
 * the versioned files in `src/main/resources/migrations/` build. The versioned files are applied
 * to a scratch database, and then:
 *
 * - `spirit diff` from that database to the declarative files must find nothing to change, and
 * - every declarative file must hold that database's `SHOW CREATE TABLE` output verbatim, so the
 *   files stay exactly what regenerating them would write.
 */
class DeclarativeSchemaTest {
  @Test
  fun declarativeSchemaMatchesVersionedFiles() {
    val spirit = File(
      requireNotNull(System.getProperty("backfila.spirit")) {
        "the backfila.spirit system property must name the Spirit binary; run this test with Gradle"
      },
    )

    val problems = DeclarativeSchema.connect().use { connection ->
      try {
        DeclarativeSchema.recreateDatabase(connection, MIGRATED)
        DeclarativeSchema.applyMigrations(connection, MIGRATED)

        spiritProblems(DeclarativeSchema.spiritDiff(spirit, MIGRATED)) +
          fileProblems(
            migrated = DeclarativeSchema.showCreateTables(connection, MIGRATED),
            files = DeclarativeSchema.declarativeFiles().mapValues { (_, file) -> file.readText() },
          )
      } finally {
        DeclarativeSchema.dropDatabase(connection, MIGRATED)
      }
    }

    if (problems.isNotEmpty()) {
      fail(
        buildString {
          appendLine(
            "The declarative schema in src/main/resources/${DeclarativeSchema.DECLARATIVE_RESOURCE} " +
              "does not match the versioned files in src/main/resources/${DeclarativeSchema.MIGRATIONS_RESOURCE}.",
          )
          appendLine(
            "Regenerate it with `${DeclarativeSchema.REGENERATE_COMMAND}` and commit the result.",
          )
          problems.forEach { appendLine().appendLine(it) }
        },
      )
    }
  }

  private fun spiritProblems(statements: List<String>): List<String> {
    if (statements.isEmpty()) return emptyList()
    return listOf(
      "spirit diff would change the database built from the versioned files to match the " +
        "declarative files:\n" + statements.joinToString("\n") { "  $it" },
    )
  }

  private fun fileProblems(migrated: Map<String, String>, files: Map<String, String>): List<String> {
    val problems = mutableListOf<String>()
    for (table in (migrated.keys + files.keys).sorted()) {
      val showCreateTable = migrated[table]
      val file = files[table]
      when {
        showCreateTable == null ->
          problems += "$table.sql: no table named $table is created by the versioned files"
        file == null ->
          problems += "$table.sql: missing"
        file != DeclarativeSchema.declarativeFileContents(showCreateTable) ->
          problems += "$table.sql: not the SHOW CREATE TABLE output of the versioned files\n" +
            diff(
              "versioned files",
              DeclarativeSchema.declarativeFileContents(showCreateTable),
              "$table.sql",
              file,
            )
      }
    }
    return problems
  }

  /** A minimal line diff, enough to point at the column or index that changed. */
  private fun diff(fromLabel: String, from: String, toLabel: String, to: String): String {
    val a = from.lines()
    val b = to.lines()
    val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
    for (i in a.indices.reversed()) {
      for (j in b.indices.reversed()) {
        lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
      }
    }
    return buildString {
      appendLine("--- $fromLabel")
      append("+++ $toLabel")
      var i = 0
      var j = 0
      while (i < a.size || j < b.size) {
        when {
          i < a.size && j < b.size && a[i] == b[j] -> append("\n  ").append(a[i++]).also { j++ }
          i < a.size && (j == b.size || lcs[i + 1][j] >= lcs[i][j + 1]) -> append("\n- ").append(a[i++])
          else -> append("\n+ ").append(b[j++])
        }
      }
    }
  }

  private companion object {
    const val MIGRATED = "backfila_declarative_schema_test_migrated"
  }
}
