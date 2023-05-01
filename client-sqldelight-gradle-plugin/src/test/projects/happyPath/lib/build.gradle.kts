import app.cash.backfila.client.sqldelight.plugin.SqlDelightRecordSource

plugins {
  id("app.cash.backfila.client.sqldelight")
  id("app.cash.sqldelight")
  kotlin("jvm")
}

sqldelight {
  databases {
    create("HockeyDataDatabase") {
      packageName.set("app.cash.backfila.client.sqldelight.hockeydata")
      dialect(libs.sqldelightMysqlDialect)
      srcDirs.setFrom(listOf("resources/migrations"))
      deriveSchemaFromMigrations.set(true)
      migrationOutputDirectory.set(file("$buildDir/resources/main/migrations"))
      verifyMigrations.set(true)
    }
  }
}

backfilaSqlDelight {
  addRecordSource(
    name = "hockeyPlayerOrigin",
    database = "app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase",
    tableName = "hockeyPlayer",
    keyName = "player_number",
    keyType = "kotlin.Int",
    recordColumns = "*",
    recordType = "app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayer"
  )
}

dependencies {
  implementation("app.cash.backfila:client-sqldelight:${project.property("backfilaVersion")}")
  implementation("app.cash.backfila:client:${project.property("backfilaVersion")}")
  implementation(libs.sqldelightJdbcDriver)
}