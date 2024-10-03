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
      srcDirs.setFrom(listOf("lib/resources/migrations"))
      deriveSchemaFromMigrations.set(true)
      migrationOutputDirectory.set(file("$buildDir/resources/main/migrations"))
      verifyMigrations.set(true)
    }
  }
}

backfilaSqlDelight {
  addRecordSource(
    name = "HockeyPlayerOrigin",
    database = "app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase",
    tableName = "hockeyPlayer",
    keyName = "player_number",
    keyType = "Int",
    recordColumns = "*",
  )
}