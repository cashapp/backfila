package app.cash.backfila.client.sqldelight.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateBackfilaRecordSourceSqlTask : DefaultTask() {
  @get:Input
  abstract val backfill: Property<SqlDelightRecordSource>

  @get:OutputDirectory
  abstract val sqlDirectory: DirectoryProperty

  @TaskAction
  fun execute() {
    val table = backfill.get().tableName
    val key = backfill.get().keyName
    val where = backfill.get().whereClause
    val recordColumns = backfill.get().recordColumns

    val sqlFile = sqlDirectory.file("hello.sq").get().asFile
    sqlFile.parentFile.mkdirs()
    sqlFile.writeText(
      """
    selectOverallRange:
    SELECT min($key), max($key)
    FROM $table;

    selectInitialMaxBound:
    SELECT MAX($key) FROM
     (SELECT DISTINCT $key FROM $table
      WHERE $key >= :backfillRangeStart
        AND $key <= :backfillRangeEnd
      ORDER BY $key ASC
      LIMIT :scanSize) AS subquery;

    selectNextMaxBound:
    SELECT MAX($key) FROM
     (SELECT DISTINCT $key FROM $table
      WHERE $key > :previousEndKey
        AND $key <= :backfillRangeEnd
      ORDER BY $key ASC
      LIMIT :scanSize) AS subquery;

    produceInitialBatchFromRange:
    SELECT $key FROM $table
    WHERE $key >= :backfillRangeStart
      AND $key <= :boundingMax
    ORDER BY $key ASC
    LIMIT 1
    OFFSET :offset;

    produceNextBatchFromRange:
    SELECT $key FROM $table
    WHERE $key > :previousEndKey
      AND $key <= :boundingMax
    ORDER BY $key ASC
    LIMIT 1
    OFFSET :offset;

    countInitialBatchMatches:
    SELECT COUNT(DISTINCT $key) FROM $table
    WHERE $key >= :backfillRangeStart
      AND $key <= :boundingMax;

    countNextBatchMatches:
    SELECT COUNT(DISTINCT $key) FROM $table
    WHERE $key > :previousEndKey
      AND $key <= :boundingMax;

    getInitialStartKeyAndScanCount:
    SELECT MIN($key), COUNT(*) FROM $table
    WHERE $key >= :backfillRangeStart
      AND $key <= :batchEnd;

    getNextStartKeyAndScanCount:
    SELECT MIN($key), COUNT(*) FROM $table
    WHERE $key > :previousEndKey
      AND $key <= :batchEnd;

    getBatch:
    SELECT $recordColumns FROM $table
    WHERE $key >= :start
      AND $key <= :end
    ORDER BY $key ASC;

      """.trimIndent(),
    )
  }
}
