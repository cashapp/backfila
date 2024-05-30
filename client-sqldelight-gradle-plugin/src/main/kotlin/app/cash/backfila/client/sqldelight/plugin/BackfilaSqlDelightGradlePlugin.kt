package app.cash.backfila.client.sqldelight.plugin

import java.io.Serializable
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

class BackfilaSqlDelightGradlePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val backfilaExtension = target.extensions.create("backfila", BackfilaSqlDelightExtension::class.java)

    backfilaExtension.backfills.all { backfill ->
      val sqlDirectory = target.layout.buildDirectory.dir("backfilaSqlDelight/${backfill.name}/sql")
      val kotlinDirectory = target.layout.buildDirectory.dir("backfilaSqlDelight/${backfill.name}/kotlin")

      val sqlTask = target.tasks.register(
        "generateBackfilaSqlDelightSql${backfill.name.capitalize()}",
        GenerateBackfilaSqlDelightSqlTask::class.java,
      ) {
        it.backfill.set(backfill.backfill)
        it.sqlDirectory.set(sqlDirectory)
      }

      target.extensions.getByType(app.cash.sqldelight.gradle.SqlDelightExtension::class.java)
        .databases.getByName(backfill.backfill.get().database).srcDirs.from(sqlDirectory)

      val kotlinTask = target.tasks.register(
        "generateBackfilaSqlDelightKotlin${backfill.name.capitalize()}",
        GenerateBackfilaSqlDelightKotlinTask::class.java,
      ) {
        it.backfill.set(backfill.backfill)
        it.kotlinDirectory.set(kotlinDirectory)
      }

      target.kotlinExtension.sourceSets.getByName("main").kotlin.srcDir(kotlinTask)
    }

    // Create an extension
    // When the extension is used, we'll create tasks

    /*

  backfila {
    sqlDelightBackfill(
    )
  }

  we'll creat a task called

     generateBackfilaSqlDelightHockeyPlayerOrigin

     the task will do these things:
      - generate a .sq file
      - tell SQLDelight about that .sq file
      - generate a .kt file
      - tell the Kotlin source sets about it
     */
  }
}

abstract class GenerateBackfilaSqlDelightSqlTask : DefaultTask() {
  @get:Input
  abstract val backfill: Property<SqlDelightBackfill>

  @get:OutputDirectory
  abstract val sqlDirectory: DirectoryProperty

  @TaskAction
  fun execute() {
    val sqlFile = sqlDirectory.file("hello.sq").get().asFile
    sqlFile.parentFile.mkdirs()
    sqlFile.writeText("backfill ${backfill.get()}")
  }
}

abstract class GenerateBackfilaSqlDelightKotlinTask : DefaultTask() {
  @get:Input
  abstract val backfill: Property<SqlDelightBackfill>

  @get:OutputDirectory
  abstract val kotlinDirectory: DirectoryProperty

  @TaskAction
  fun execute() {
    val ktFile = kotlinDirectory.file("Hello.kt").get().asFile
    ktFile.parentFile.mkdirs()
    ktFile.writeText("fun loadFrom${backfill.get().table}() { }")
  }
}

abstract class BackfilaSqlDelightExtension {
  abstract val backfills: NamedDomainObjectContainer<SqlDelightBackfillEntry>

  fun addBackfill(
    name: String,
    database: String,
    tableName: String,
    keyType: String,
    table: String,
    recordColumns: String,
  ) {
    backfills.create(name).backfill.set(
      SqlDelightBackfill(
        database = database,
        tableName = tableName,
        keyType = keyType,
        table = table,
        recordColumns = recordColumns,
      ),
    )
  }
}

abstract class SqlDelightBackfillEntry(val name: String) {
  abstract val backfill: Property<SqlDelightBackfill>
}

data class SqlDelightBackfill(
  val database: String,
  val tableName: String,
  val keyType: String,
  val table: String,
  val recordColumns: String,
) : Serializable
