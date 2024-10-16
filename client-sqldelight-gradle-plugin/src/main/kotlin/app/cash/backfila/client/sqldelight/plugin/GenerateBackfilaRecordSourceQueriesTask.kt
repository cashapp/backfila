package app.cash.backfila.client.sqldelight.plugin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateBackfilaRecordSourceQueriesTask : DefaultTask() {
  @get:Input
  abstract val backfill: Property<SqlDelightRecordSource>

  @get:Input
  abstract val packageName: Property<String>

  @get:OutputDirectory
  abstract val kotlinDirectory: DirectoryProperty

  @TaskAction
  fun execute() {
    val backfillConfig = backfill.get()
    val name = backfillConfig.name.replaceFirstChar { it.uppercase() }
    val packageName = packageName.get()
    val fileName = "${name}RecordSourceQueries"
    val targetDirectory = kotlinDirectory.asFile.get()
    targetDirectory.mkdirs()

    val databaseClass = ClassName.bestGuess(backfillConfig.database)
    val queriesFunctionName = "${backfillConfig.name}Queries".replaceFirstChar { it.lowercase() }

    val keyType = backfillConfig.keyType
    val recordType = backfillConfig.recordType
    val recordSourceQueriesType = ClassName("app.cash.backfila.client.sqldelight", "SqlDelightRecordSourceQueries")
    val returnType = recordSourceQueriesType.parameterizedBy(ClassName.bestGuess(keyType), ClassName.bestGuess(recordType))

    val poetFile = FileSpec.builder(packageName, fileName)
      .addFunction(
        FunSpec.builder("get${name}Queries")
          .addParameter("database", databaseClass)
          .returns(returnType)
          .addStatement(
            """
            | return %T.create(
            |   database.$queriesFunctionName.selectAbsoluteRange { min, max -> %T.MinMax(min, max) },
            |   { rangeStart: $keyType, rangeEnd: $keyType, scanSize: Long ->
            |     database.$queriesFunctionName.selectInitialMaxBound(rangeStart, rangeEnd, scanSize) {
            |       %T.NullKeyContainer(
            |         it,
            |       )
            |     }
            |   },
            |   { previousEndKey: $keyType, rangeEnd: $keyType, scanSize: Long ->
            |     database.$queriesFunctionName.selectNextMaxBound(
            |       previousEndKey,
            |       rangeEnd,
            |       scanSize,
            |     ) { %T.NullKeyContainer(it) }
            |   },
            |   { rangeStart: $keyType, rangeEnd: $keyType, offset: Long -> database.$queriesFunctionName.produceInitialBatchFromRange(rangeStart, rangeEnd, offset) },
            |   { previousEndKey: $keyType, rangeEnd: $keyType, offset: Long -> database.$queriesFunctionName.produceNextBatchFromRange(previousEndKey, rangeEnd, offset) },
            |   { rangeStart: $keyType, boundingMax: $keyType -> database.$queriesFunctionName.countInitialBatchMatches(rangeStart, boundingMax) },
            |   { previousEndKey: $keyType, boundingMax: $keyType -> database.$queriesFunctionName.countNextBatchMatches(previousEndKey, boundingMax) },
            |   { rangeStart: $keyType, rangeEnd: $keyType ->
            |     database.$queriesFunctionName.getInitialStartKeyAndScanCount(rangeStart, rangeEnd) { min, count ->
            |       %T.MinAndCount(
            |         min,
            |         count,
            |       )
            |     }
            |   },
            |   { previousEndKey: $keyType, rangeEnd: $keyType ->
            |     database.$queriesFunctionName.getNextStartKeyAndScanCount(
            |       previousEndKey,
            |       rangeEnd,
            |     ) { min, count -> %T.MinAndCount(min, count) }
            |   },
            |   { start: $keyType, end: $keyType -> database.$queriesFunctionName.getBatch(start, end) },
            | )
            |
            """.trimMargin(),
            recordSourceQueriesType,
            recordSourceQueriesType,
            recordSourceQueriesType,
            recordSourceQueriesType,
            recordSourceQueriesType,
            recordSourceQueriesType,
          )
          .build(),
      ).build()

    poetFile.writeTo(targetDirectory)
  }
}
