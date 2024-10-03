package app.cash.backfila.client.sqldelight.plugin

import java.io.Serializable
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

class BackfilaSqlDelightGradlePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val backfilaExtension = target.extensions.create("backfilaSqlDelight", BackfilaSqlDelightExtension::class.java)

    backfilaExtension.backfills.all { backfill ->
      check(!backfill.name.contains("\\s")) { "Backfill `name` is not allowed to contain whitespace." }
      val sqlDirectory = target.layout.buildDirectory.dir("backfilaSqlDelight/${backfill.name}/sql")
      val kotlinDirectory = target.layout.buildDirectory.dir("backfilaSqlDelight/${backfill.name}/kotlin")

      val sqlTask = target.tasks.register(
        "generateBackfilaRecordSourceSql${backfill.name.replaceFirstChar { it.uppercase() }}",
        GenerateBackfilaRecordSourceSqlTask::class.java,
      ) {
        it.backfill.set(backfill.backfill)
        it.sqlDirectory.set(sqlDirectory)
      }

      val sqlDelightExtension = target.extensions.findByType(app.cash.sqldelight.gradle.SqlDelightExtension::class.java)
      check(sqlDelightExtension != null) {
        "The Backfila gradle plugin requires the SqlDelight gradle plugin to function."
      }

      sqlDelightExtension.databases.all {
        if (it.name == backfill.backfill.get().database) {
          it.srcDirs.from(sqlDirectory)
        }
      }

      val kotlinTask = target.tasks.register(
        "generateBackfilaRecordSourceQueries${backfill.name.replaceFirstChar { it.uppercase() }}",
        GenerateBackfilaRecordSourceQueriesTask::class.java,
      ) {
        it.dependsOn(sqlTask)
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

     generateBackfilaSqlDelight$tableOrigin

     the task will do these things:
      - generate a .sq file
      - tell SQLDelight about that .sq file
      - generate a .kt file
      - tell the Kotlin source sets about it
     */
  }
}

abstract class BackfilaSqlDelightExtension {
  abstract val backfills: NamedDomainObjectContainer<SqlDelightRecordSourceEntry>

  fun addRecordSource(
    name: String,
    database: String,
    tableName: String,
    keyName: String,
    keyType: String,
    recordColumns: String,
    whereClause: String = "1 = 1",
  ) {
    backfills.create(name).backfill.set(
      SqlDelightRecordSource(
        name = name,
        database = database,
        tableName = tableName,
        keyName = keyName,
        keyType = keyType,
        recordColumns = recordColumns,
        whereClause = whereClause,
      ),
    )
  }
}

abstract class SqlDelightRecordSourceEntry(val name: String) {
  abstract val backfill: Property<SqlDelightRecordSource>
}

data class SqlDelightRecordSource(
  val name: String,
  val database: String,
  val tableName: String,
  val keyName: String, // TODO: Maybe eventually also support compound keys.
  val keyType: String, // TODO: Get this information directly from SQLDelight
  val recordColumns: String,
  val whereClause: String,
) : Serializable
