package app.cash.backfila.service.persistence

import java.io.File

/**
 * Rewrites the declarative schema from the versioned files: applies `classpath:/migrations` to a
 * scratch database and writes each table's `SHOW CREATE TABLE` output to `<table>.sql` in the
 * directory given as the only argument, replacing the `.sql` files already there.
 *
 * Run it with `gradle :service:regenerateDeclarativeSchema`, against a MySQL 8 server on
 * localhost:3306 that accepts root with an empty password, as the tests do.
 */
fun main(args: Array<String>) {
  val outputDirectory = File(requireNotNull(args.singleOrNull()) { "usage: <output directory>" })
  val database = "backfila_declarative_schema_regenerate"

  val tables = DeclarativeSchema.connect().use { connection ->
    try {
      DeclarativeSchema.recreateDatabase(connection, database)
      DeclarativeSchema.applySchemaChanges(connection, database)
      DeclarativeSchema.showCreateTables(connection, database)
    } finally {
      DeclarativeSchema.dropDatabase(connection, database)
    }
  }

  outputDirectory.mkdirs()
  outputDirectory.listFiles { file -> file.isFile && file.name.endsWith(".sql") }
    ?.forEach { check(it.delete()) { "could not delete $it" } }
  for ((table, showCreateTable) in tables) {
    File(outputDirectory, "$table.sql")
      .writeText(DeclarativeSchema.declarativeFileContents(showCreateTable))
  }
  println("Wrote ${tables.size} tables to $outputDirectory")
}
