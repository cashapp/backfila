package app.cash.backfila.client.sqldelight.plugin

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateBackfilaRecordSourceSqlTask : DefaultTask() {
  @get:Input
  abstract val backfill: Property<SqlDelightRecordSource>

  @get:Input
  abstract val packageName: Property<String>

  @get:OutputDirectory
  abstract val sqlDirectory: DirectoryProperty

  @TaskAction
  fun execute() {
    val backfillConfig = backfill.get()
    val table = backfillConfig.tableName
    val key = backfillConfig.keyName
    val where = backfillConfig.whereClause
    val recordColumns = backfillConfig.recordColumns
    val name = backfillConfig.name.replaceFirstChar { it.uppercase() }
    val packageDirs = packageName.get().replace('.', File.separatorChar)

    val sqlFile = File(sqlDirectory.get().asFile, "$packageDirs/$name.sq")
    sqlFile.parentFile.mkdirs()
    sqlFile.writeText(
      """
    selectAbsoluteRange:
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

    getInitialStartKeyAndScanCount:
    SELECT MIN($key), COUNT(*) FROM $table
    WHERE $key >= :backfillRangeStart
      AND $key <= :batchEnd;

    getNextStartKeyAndScanCount:
    SELECT MIN($key), COUNT(*) FROM $table
    WHERE $key > :previousEndKey
      AND $key <= :batchEnd;

    produceInitialBatchFromRange:
    SELECT $key FROM $table
    WHERE $key >= :backfillRangeStart
      AND $key <= :boundingMax
      AND ( $where )
    ORDER BY $key ASC
    LIMIT 1
    OFFSET :offset;

    produceNextBatchFromRange:
    SELECT $key FROM $table
    WHERE $key > :previousEndKey
      AND $key <= :boundingMax
      AND ( $where )
    ORDER BY $key ASC
    LIMIT 1
    OFFSET :offset;

    countInitialBatchMatches:
    SELECT COUNT(DISTINCT $key) FROM $table
    WHERE $key >= :backfillRangeStart
      AND $key <= :boundingMax
      AND ( $where );

    countNextBatchMatches:
    SELECT COUNT(DISTINCT $key) FROM $table
    WHERE $key > :previousEndKey
      AND $key <= :boundingMax
      AND ( $where );

    getBatch:
    SELECT $recordColumns FROM $table
    WHERE $key >= :start
      AND $key <= :end
      AND ( $where )
    ORDER BY $key ASC;

      """.trimIndent(),
    )
  }
}
