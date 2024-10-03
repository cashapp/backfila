package app.cash.backfila.client.sqldelight.plugin

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateBackfilaRecordSourceQueriesTask : DefaultTask() {
  @get:Input
  abstract val backfill: Property<SqlDelightRecordSource>

  @get:OutputDirectory
  abstract val kotlinDirectory: DirectoryProperty

  @TaskAction
  fun execute() {
    val name = backfill.get().name.replaceFirstChar { it.uppercase() }
    val fileName = "${name}RecordSourceQueries"
    val ktFile = kotlinDirectory.file("$fileName.kt").get().asFile
    ktFile.parentFile.mkdirs()

    val databaseClass = try {
      Class.forName(backfill.get().database).kotlin
    } catch (e: ClassNotFoundException) {
      throw ClassNotFoundException("Did not find the expected database class ${backfill.get().database}.", e)
    }

    val poetFile = FileSpec.builder("", fileName)
      .addFunction(
        FunSpec.builder("get${name}Queries")
          .addParameter("database", databaseClass)
          .returns(SqlDelightRecordSource::class)
          .addCode(
            """
            | return SqlDelightRecordSourceQueries.create(
            |   database.allHockeyPlayersBackfillQueries.selectAbsoluteRange { min, max -> SqlDelightRecordSourceQueries.MinMax(min, max) },
            |   { rangeStart: Int, rangeEnd: Int, scanSize: Long ->
            |     database.allHockeyPlayersBackfillQueries.selectInitialMaxBound(rangeStart, rangeEnd, scanSize) {
            |       SqlDelightRecordSourceQueries.NullKeyContainer(
            |         it,
            |       )
            |     }
            |   },
            |   { previousEndKey: Int, rangeEnd: Int, scanSize: Long ->
            |     database.allHockeyPlayersBackfillQueries.selectNextMaxBound(
            |       previousEndKey,
            |       rangeEnd,
            |       scanSize,
            |     ) { SqlDelightRecordSourceQueries.NullKeyContainer(it) }
            |   },
            |   { rangeStart: Int, rangeEnd: Int, offset: Long -> database.allHockeyPlayersBackfillQueries.produceInitialBatchFromRange(rangeStart, rangeEnd, offset) },
            |   { previousEndKey: Int, rangeEnd: Int, offset: Long -> database.allHockeyPlayersBackfillQueries.produceNextBatchFromRange(previousEndKey, rangeEnd, offset) },
            |   { rangeStart: Int, boundingMax: Int -> database.allHockeyPlayersBackfillQueries.countInitialBatchMatches(rangeStart, boundingMax) },
            |   { previousEndKey: Int, boundingMax: Int -> database.allHockeyPlayersBackfillQueries.countNextBatchMatches(previousEndKey, boundingMax) },
            |   { rangeStart: Int, rangeEnd: Int ->
            |     database.allHockeyPlayersBackfillQueries.getInitialStartKeyAndScanCount(rangeStart, rangeEnd) { min, count ->
            |       SqlDelightRecordSourceQueries.MinAndCount(
            |         min,
            |         count,
            |       )
            |     }
            |   },
            |   { previousEndKey: Int, rangeEnd: Int ->
            |     database.allHockeyPlayersBackfillQueries.getNextStartKeyAndScanCount(
            |       previousEndKey,
            |       rangeEnd,
            |     ) { min, count -> SqlDelightRecordSourceQueries.MinAndCount(min, count) }
            |   },
            |   { start: Int, end: Int -> database.allHockeyPlayersBackfillQueries.getBatch(start, end) },
            | )
            |
            """.trimMargin(),
          )
          .build(),
      ).build()

    poetFile.writeTo(ktFile)
  }
}
