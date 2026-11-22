package app.cash.backfila.service.persistence

import kotlin.test.fail
import org.junit.jupiter.api.Test

/**
 * The declarative files in `src/main/resources/mysql/schema/backfila/` describe exactly the schema
 * the versioned files in `src/main/resources/migrations/` build. The versioned files are applied
 * to one scratch database and the declarative files loaded into another; every table's
 * `SHOW CREATE TABLE` must match between the two, ignoring the `AUTO_INCREMENT` counter, and every
 * declarative file must hold that output verbatim.
 */
class DeclarativeSchemaTest {
  @Test
  fun declarativeSchemaMatchesVersionedFiles() {
    val problems = DeclarativeSchema.connect().use { connection ->
      try {
        DeclarativeSchema.recreateDatabase(connection, MIGRATED)
        DeclarativeSchema.applyMigrations(connection, MIGRATED)
        DeclarativeSchema.recreateDatabase(connection, DECLARED)
        DeclarativeSchema.applyDeclarative(connection, DECLARED)

        compare(
          migrated = DeclarativeSchema.showCreateTables(connection, MIGRATED),
          declared = DeclarativeSchema.showCreateTables(connection, DECLARED),
          files = DeclarativeSchema.declarativeFiles().mapValues { (_, file) -> file.readText() },
        )
      } finally {
        DeclarativeSchema.dropDatabase(connection, MIGRATED)
        DeclarativeSchema.dropDatabase(connection, DECLARED)
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

  private fun compare(
    migrated: Map<String, String>,
    declared: Map<String, String>,
    files: Map<String, String>,
  ): List<String> {
    val problems = mutableListOf<String>()
    for (table in (migrated.keys + declared.keys + files.keys).sorted()) {
      val expected = migrated[table]
      when {
        expected == null && table in files ->
          problems += "$table.sql: no table named $table is created by the versioned files"
        expected == null ->
          problems += "$table: created by a declarative file under a different name"
        table !in files ->
          problems += "$table.sql: missing"
        declared[table] == null ->
          problems += "$table.sql: does not create a table named $table"
        declared[table] != expected ->
          problems += "$table: SHOW CREATE TABLE differs\n" +
            diff("versioned files", expected, "declarative file", declared.getValue(table))
        // Same schema, but the file is not byte-for-byte what regenerating would write.
        files[table] != DeclarativeSchema.declarativeFileContents(expected) ->
          problems += "$table.sql: not the canonical SHOW CREATE TABLE output\n" +
            diff(
              "expected",
              DeclarativeSchema.declarativeFileContents(expected),
              "actual",
              files.getValue(table),
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
    const val DECLARED = "backfila_declarative_schema_test_declared"
  }
}
