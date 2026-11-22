package app.cash.backfila.service.persistence

import java.io.File
import java.net.URL
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

/**
 * Backfila's schema in declarative form: one `CREATE TABLE` per table under
 * `src/main/resources/mysql/schema/backfila/`, shipped in the service jar next to the versioned
 * files in `src/main/resources/migrations/`.
 *
 * Each declarative file holds exactly what MySQL 8's `SHOW CREATE TABLE` prints for that table
 * once every versioned file has been applied to an empty database, without the `AUTO_INCREMENT`
 * counter, followed by `;`. The versioned files stay the source of truth: change the schema by
 * adding a versioned file, then regenerate the declarative files with [REGENERATE_COMMAND].
 *
 * Everything here runs against the MySQL the rest of the test suite uses (root on
 * localhost:3306) and works in scratch databases it drops and recreates.
 */
internal object DeclarativeSchema {
  const val MIGRATIONS_RESOURCE = "migrations"
  const val DECLARATIVE_RESOURCE = "mysql/schema/backfila"
  const val REGENERATE_COMMAND = "gradle :service:regenerateDeclarativeSchema"

  /**
   * The versioned files are named like Misk's default `migrations_resources_regex` expects, and
   * applied in version order.
   */
  private val MIGRATION_FILE_NAME = Regex("""v(\d+)__[^/]+\.sql""")
  private val AUTO_INCREMENT_COUNTER = Regex(""" AUTO_INCREMENT=\d+""")

  /**
   * Multi-statement execution lets each file run as a single string, so MySQL itself splits the
   * statements rather than a hand-written parser.
   */
  fun connect(): Connection =
    DriverManager.getConnection("jdbc:mysql://localhost:3306/?allowMultiQueries=true", "root", "")

  /**
   * Drops and recreates [database]. The character set and collation are pinned to MySQL 8's
   * defaults so a server configured with different defaults produces the same output.
   */
  fun recreateDatabase(connection: Connection, database: String) {
    connection.execute("DROP DATABASE IF EXISTS `$database`")
    connection.execute(
      "CREATE DATABASE `$database` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci",
    )
  }

  fun dropDatabase(connection: Connection, database: String) {
    connection.execute("DROP DATABASE IF EXISTS `$database`")
  }

  /** Applies every versioned file in `classpath:/migrations` to [database], in version order. */
  fun applyMigrations(connection: Connection, database: String) {
    val migrations = sqlResources(MIGRATIONS_RESOURCE).sortedBy { versionOf(it.name) }
    check(migrations.isNotEmpty()) { "no versioned files found in classpath:/$MIGRATIONS_RESOURCE" }
    connection.catalog = database
    for (migration in migrations) {
      connection.executeFile(migration)
    }
  }

  /** Applies every declarative file in `classpath:/mysql/schema/backfila` to [database]. */
  fun applyDeclarative(connection: Connection, database: String) {
    connection.catalog = database
    for (file in declarativeFiles().values) {
      connection.executeFile(file)
    }
  }

  /** The declarative files on the classpath, keyed by the table each one is named after. */
  fun declarativeFiles(): Map<String, File> =
    sqlResources(DECLARATIVE_RESOURCE).associateBy { it.name.removeSuffix(".sql") }.toSortedMap()

  /**
   * Returns each base table's `SHOW CREATE TABLE` output in [database], keyed by table name, with
   * the `AUTO_INCREMENT` counter removed since it tracks rows rather than schema.
   */
  fun showCreateTables(connection: Connection, database: String): Map<String, String> {
    val tables = mutableListOf<String>()
    connection.createStatement().use { statement ->
      statement.executeQuery(
        "SHOW FULL TABLES FROM `$database` WHERE Table_type = 'BASE TABLE'",
      ).use { rows ->
        while (rows.next()) tables += rows.getString(1)
      }
    }
    return tables.associateWith { table ->
      connection.createStatement().use { statement ->
        statement.executeQuery("SHOW CREATE TABLE `$database`.`$table`").use { rows ->
          check(rows.next()) { "SHOW CREATE TABLE returned no rows for $database.$table" }
          AUTO_INCREMENT_COUNTER.replace(rows.getString(2), "")
        }
      }
    }.toSortedMap()
  }

  /** The exact contents of a table's declarative file. */
  fun declarativeFileContents(showCreateTable: String): String = "$showCreateTable;\n"

  private fun versionOf(fileName: String): Int {
    val match = requireNotNull(MIGRATION_FILE_NAME.matchEntire(fileName)) {
      "unexpected file in classpath:/$MIGRATIONS_RESOURCE: $fileName"
    }
    return match.groupValues[1].toInt()
  }

  private fun sqlResources(path: String): List<File> {
    val url: URL = requireNotNull(javaClass.classLoader.getResource(path)) {
      "classpath:/$path not found"
    }
    check(url.protocol == "file") { "classpath:/$path must be a directory, got $url" }
    return File(url.toURI()).listFiles { file -> file.isFile && file.name.endsWith(".sql") }
      ?.sortedBy { it.name }
      .orEmpty()
  }

  private fun Connection.execute(sql: String) {
    createStatement().use { it.execute(sql) }
  }

  private fun Connection.executeFile(file: File) {
    try {
      createStatement().use { statement ->
        statement.execute(file.readText())
        statement.drainResults()
      }
    } catch (e: Exception) {
      throw IllegalStateException("failed to apply ${file.name}: ${e.message}", e)
    }
  }

  /**
   * With multi-statement execution, an error in any statement after the first only surfaces
   * while stepping through the results, so every result is consumed.
   */
  private fun Statement.drainResults() {
    var hasNext = true
    while (hasNext) {
      // getMoreResults advances to the next statement's result; -1 means there are none left.
      hasNext = moreResults || updateCount != -1
    }
  }
}
