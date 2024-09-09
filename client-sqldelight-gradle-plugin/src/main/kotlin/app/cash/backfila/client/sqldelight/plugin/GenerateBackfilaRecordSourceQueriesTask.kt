package app.cash.backfila.client.sqldelight.plugin

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
    val ktFile = kotlinDirectory.file("Hello.kt").get().asFile
    ktFile.parentFile.mkdirs()
    ktFile.writeText("fun loadFrom${backfill.get().tableName}() { }")
  }
}
