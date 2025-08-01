package app.cash.backfila.client.sqldelight.plugin

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.OVERRIDE
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
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
    val lowerName = backfillConfig.name.replaceFirstChar { it.lowercase() }
    val packageName = packageName.get()
    val className = "${name}RecordSourceConfig"
    val targetDirectory = kotlinDirectory.asFile.get()
    targetDirectory.mkdirs()

    // Find and specify types
    val databaseType = ClassName.bestGuess(backfillConfig.database)
    val queriesFunctionName = "${backfillConfig.name}Queries".replaceFirstChar { it.lowercase() }

    val keyType = ClassName.bestGuess(backfillConfig.keyType)
    val keyEncoderType = ClassName("app.cash.backfila.client.sqldelight", "KeyEncoder")
      .parameterizedBy(keyType)
    val myKeyEncoderType = ClassName.bestGuess(backfillConfig.keyEncoder)

    val parameterizedRecordType = ClassName("app.cash.backfila.client.sqldelight", "SqlDelightRecordSourceConfig")
      .parameterizedBy(keyType, ClassName.bestGuess(backfillConfig.recordType))

    val minMaxType = ClassName("app.cash.backfila.client.sqldelight", "MinMax")
      .parameterizedBy(keyType)
    val nullKeyContainerType = ClassName("app.cash.backfila.client.sqldelight", "NullKeyContainer")
      .parameterizedBy(keyType)
    val minAndCountType = ClassName("app.cash.backfila.client.sqldelight", "MinAndCount")
      .parameterizedBy(keyType)

    // Return query types
    val queryType = ClassName("app.cash.sqldelight", "Query")
    val minMaxQueryType = queryType.parameterizedBy(minMaxType)
    val nullKeyContainerQueryType = queryType.parameterizedBy(nullKeyContainerType)
    val minAndCountQueryType = queryType.parameterizedBy(minAndCountType)
    val keyQueryType = queryType.parameterizedBy(keyType)
    val longQueryType = queryType.parameterizedBy(LONG)
    val recordQueryType = queryType.parameterizedBy(ClassName.bestGuess(backfillConfig.recordType))

    // Generate the file.
    val poetFile = FileSpec.builder(packageName, className)
      .addType(
        TypeSpec.classBuilder(ClassName(packageName, className))
          .addSuperinterface(parameterizedRecordType)
          .primaryConstructor(
            FunSpec.constructorBuilder()
              .addParameter("database", databaseType)
              .build(),
          ).addProperty(
            PropertySpec.builder("database", databaseType, PRIVATE)
              .initializer("database")
              .build(),
          ).addProperty(
            PropertySpec.builder("keyEncoder", keyEncoderType, OVERRIDE)
              .initializer("%T", myKeyEncoderType)
              .build(),
          ).addFunction(
            FunSpec.builder("selectAbsoluteRange")
              .returns(minMaxQueryType)
              .addStatement("return database.%L.%LSelectAbsoluteRange { min, max -> %T(min, max) }", queriesFunctionName, lowerName, minMaxType)
              .addModifiers(OVERRIDE)
              .build(),
          ).addFunction(
            FunSpec.builder("selectInitialMaxBound")
              .addParameter("rangeStart", keyType)
              .addParameter("rangeEnd", keyType)
              .addParameter("scanSize", LONG)
              .returns(nullKeyContainerQueryType)
              .addStatement("return database.%L.%LSelectInitialMaxBound(rangeStart, rangeEnd, scanSize) { %T(it) }", queriesFunctionName, lowerName, nullKeyContainerType)
              .addModifiers(OVERRIDE)
              .build(),
          ).addFunction(
            FunSpec.builder("selectNextMaxBound")
              .addParameter("previousEndKey", keyType)
              .addParameter("rangeEnd", keyType)
              .addParameter("scanSize", LONG)
              .returns(nullKeyContainerQueryType)
              .addStatement("return database.%L.%LSelectNextMaxBound(previousEndKey, rangeEnd, scanSize) { %T(it) }", queriesFunctionName, lowerName, nullKeyContainerType)
              .addModifiers(OVERRIDE)
              .build(),
          ).addFunction(
            FunSpec.builder("produceInitialBatchFromRange")
              .addParameter("rangeStart", keyType)
              .addParameter("boundingMax", keyType)
              .addParameter("offset", LONG)
              .returns(keyQueryType)
              .addStatement("return database.%L.%LProduceInitialBatchFromRange(rangeStart, boundingMax, offset)", queriesFunctionName, lowerName)
              .addModifiers(OVERRIDE)
              .build(),
          ).addFunction(
            FunSpec.builder("produceNextBatchFromRange")
              .addParameter("previousEndKey", keyType)
              .addParameter("boundingMax", keyType)
              .addParameter("offset", LONG)
              .returns(keyQueryType)
              .addStatement("return database.%L.%LProduceNextBatchFromRange(previousEndKey, boundingMax, offset)", queriesFunctionName, lowerName)
              .addModifiers(OVERRIDE)
              .build(),
          ).addFunction(
            FunSpec.builder("countInitialBatchMatches")
              .addParameter("rangeStart", keyType)
              .addParameter("boundingMax", keyType)
              .returns(longQueryType)
              .addStatement("return database.%L.%LCountInitialBatchMatches(rangeStart, boundingMax)", queriesFunctionName, lowerName)
              .addModifiers(OVERRIDE)
              .build(),
          ).addFunction(
            FunSpec.builder("countNextBatchMatches")
              .addParameter("previousEndKey", keyType)
              .addParameter("boundingMax", keyType)
              .returns(longQueryType)
              .addStatement("return database.%L.%LCountNextBatchMatches(previousEndKey, boundingMax)", queriesFunctionName, lowerName)
              .addModifiers(OVERRIDE)
              .build(),
          ).addFunction(
            FunSpec.builder("getInitialStartKeyAndScanCount")
              .addParameter("rangeStart", keyType)
              .addParameter("batchEnd", keyType)
              .returns(minAndCountQueryType)
              .addStatement("return database.%L.%LGetInitialStartKeyAndScanCount(rangeStart, batchEnd) { min, count -> %T(min, count) }", queriesFunctionName, lowerName, minAndCountType)
              .addModifiers(OVERRIDE)
              .build(),
          ).addFunction(
            FunSpec.builder("getNextStartKeyAndScanCount")
              .addParameter("previousEndKey", keyType)
              .addParameter("batchEnd", keyType)
              .returns(minAndCountQueryType)
              .addStatement("return database.%L.%LGetNextStartKeyAndScanCount(previousEndKey, batchEnd) { min, count -> %T(min, count) }", queriesFunctionName, lowerName, minAndCountType)
              .addModifiers(OVERRIDE)
              .build(),
          ).addFunction(
            FunSpec.builder("getBatch")
              .addParameter("start", keyType)
              .addParameter("end", keyType)
              .returns(recordQueryType)
              .addStatement("return database.%L.%LGetBatch(start, end)", queriesFunctionName, lowerName)
              .addModifiers(OVERRIDE)
              .build(),
          ).build(),
      ).build()

    poetFile.writeTo(targetDirectory)
  }
}
