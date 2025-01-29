package app.cash.backfila.client.sqldelight.plugin

import app.cash.sqldelight.gradle.GenerateMigrationOutputTask
import app.cash.sqldelight.gradle.SqlDelightExtension
import app.cash.sqldelight.gradle.SqlDelightTask
import java.io.Serializable
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension

class BackfilaSqlDelightGradlePlugin : Plugin<Project> {
  override fun apply(target: Project) {
    val backfilaExtension = target.extensions.create("backfilaSqlDelight", BackfilaSqlDelightExtension::class.java)

    backfilaExtension.backfills.all { backfill ->
      check(!backfill.name.matches(Regex("\\s"))) { "Backfill `name` is not allowed to contain whitespace." }
      val baseSqlDirectory = target.layout.buildDirectory.dir("backfilaSqlDelight/${backfill.name}/sql")
      val baseKotlinDirectory = target.layout.buildDirectory.dir("backfilaSqlDelight/${backfill.name}/kotlin")
      val packageProvider = backfill.backfill.databaseProvider().packageName()
      val databaseClassNameProvider = backfill.backfill.databaseProvider().className()

      val sqlTask = target.tasks.register(
        "generateBackfilaRecordSourceSql${backfill.name.replaceFirstChar { it.uppercase() }}",
        GenerateBackfilaRecordSourceSqlTask::class.java,
      ) {
        it.backfill.set(backfill.backfill)
        it.sqlDirectory.set(baseSqlDirectory)
        it.packageName.set(packageProvider)
      }

      val sqlDelightExtension = target.extensions.findByType(SqlDelightExtension::class.java)
      check(sqlDelightExtension != null) {
        "The Backfila gradle plugin requires the SqlDelight gradle plugin to function."
      }

      sqlDelightExtension.databases.all { sqlDelightDatabase ->
        sqlDelightDatabase.srcDirs.from(
          baseSqlDirectory.map<List<TaskProvider<*>>> { _ ->
            if (packageProvider.get() == sqlDelightDatabase.packageName.get() && databaseClassNameProvider.get() == sqlDelightDatabase.name) {
              listOf(sqlTask)
            } else {
              listOf()
            }
          },
        )
      }

      // This is to unblock usage. We need these dependencies so that gradle understands that sqldelight
      // components need these. But that with take SQLDelight changes to its plugin.
      // TODO: Remove these and replace with proper source sets to the correct SQLDelight tasks.
      target.tasks.withType(SqlDelightTask::class.java) { t ->
        t.dependsOn(sqlTask)
      }
      target.tasks.withType(GenerateMigrationOutputTask::class.java) { t ->
        t.dependsOn(sqlTask)
      }

      val kotlinTask = target.tasks.register(
        "generateBackfilaRecordSourceQueries${backfill.name.replaceFirstChar { it.uppercase() }}",
        GenerateBackfilaRecordSourceQueriesTask::class.java,
      ) {
        it.dependsOn(sqlTask)
        it.backfill.set(backfill.backfill)
        it.packageName.set(packageProvider)
        it.kotlinDirectory.set(baseKotlinDirectory.map { baseDir -> baseDir.dir(packageProvider.get()) })
      }

      target.kotlinExtension.sourceSets.getByName("main").kotlin.srcDir(kotlinTask)
      target.dependencies.add("implementation", "app.cash.backfila:client-sqldelight:$VERSION")
    }
  }
}

private fun Property<SqlDelightRecordSource>.databaseProvider(): Provider<String> = map {
  it.database
}

private fun Provider<String>.packageName(): Provider<String> = map {
  it.substring(0, it.lastIndexOf('.'))
}

private fun Provider<String>.className(): Provider<String> = map {
  it.substring(it.lastIndexOf('.') + 1)
}

abstract class BackfilaSqlDelightExtension {
  abstract val backfills: NamedDomainObjectContainer<SqlDelightRecordSourceEntry>

  fun addRecordSource(
    name: String,
    database: String,
    tableName: String,
    keyName: String,
    keyType: String,
    keyEncoder: String,
    recordColumns: String,
    recordType: String,
    whereClause: String = "1 = 1",
  ) {
    backfills.create(name) {
      it.backfill.set(
        SqlDelightRecordSource(
          name = name,
          database = database,
          tableName = tableName,
          keyName = keyName,
          keyType = keyType,
          keyEncoder = keyEncoder,
          recordColumns = recordColumns,
          recordType = recordType,
          whereClause = whereClause,
        ),
      )
    }
  }
}

abstract class SqlDelightRecordSourceEntry(val name: String) {
  abstract val backfill: Property<SqlDelightRecordSource>
}

data class SqlDelightRecordSource(
  // The name of your Backfill. This will affect the generated file and class names.
  val name: String,
  // The SQLDelight database full classname.
  val database: String,
  // The name of the table this backfill is to operate on.
  val tableName: String,
  // The name of the key of the table above.
  val keyName: String, // TODO: Eventually also support compound keys.
  // The type of the key above.
  val keyType: String, // TODO: Get this information directly from SQLDelight
  // An encoder implementation for the key type above.
  val keyEncoder: String, // TODO: Automatically set this when it can.
  // The columns to be selected. These will be provided to you in the runBatch. `*` or comma notation accepted.
  val recordColumns: String,
  // The type that SQLDelight creates for the above.
  val recordType: String, // TODO: Get this information directly from SQLDelight
  // The additional where clause is optional.
  val whereClause: String,
) : Serializable
