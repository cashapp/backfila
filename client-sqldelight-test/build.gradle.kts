plugins {
  id("org.jetbrains.kotlin.jvm")
  id("app.cash.sqldelight")
  id("app.cash.backfila.client.sqldelight")
}

// TODO May have to make the other module dependent on this test module although we would have to avoid cycles.
sqldelight {
  databases {
    create("HockeyDataDatabase") {
      packageName.set("app.cash.backfila.client.sqldelight.hockeydata")
      dialect(libs.sqldelightMysqlDialect)
      srcDirs.setFrom(listOf("src/main/sqldelight", "src/main/resources/migrations"))
      deriveSchemaFromMigrations.set(true)
      migrationOutputDirectory.set(file("$buildDir/resources/main/migrations"))
      verifyMigrations.set(true)
    }
  }
}

backfilaSqlDelight {
  addRecordSource(
    name = "hockeyPlayersBackfill",
    database = "app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase",
    tableName = "hockeyPlayer",
    keyName = "player_number",
    keyType = "kotlin.Int",
    keyEncoder = "app.cash.backfila.client.sqldelight.IntKeyEncoder",
    recordColumns = "*",
    recordType = "app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayer"
  )
}

val compileKotlin by tasks.getting {
  dependsOn("generateMainHockeyDataDatabaseMigrations")
}

val generateMainHockeyDataDatabaseInterface by tasks.getting {
  dependsOn("generateBackfilaRecordSourceSqlHockeyPlayersBackfill")
}

dependencies {
  implementation(libs.guava)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.wireRuntime)
  implementation(libs.guice)
  implementation(libs.kotlinStdLib)
  implementation(libs.sqldelightJdbcDriver)
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(libs.retrofit)
  implementation(libs.retrofitMock)
  implementation(libs.retrofitMoshi)
  implementation(libs.retrofitWire)
  implementation(libs.wireMoshiAdapter)

  implementation(project(":client"))
  implementation(project(":client-sqldelight"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
  testImplementation(libs.kotlinTest)

  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-testing"))

  // ****************************************
  // For TESTING purposes only. We only want Misk for easy testing.
  // DO NOT turn these into regular dependencies.
  // ****************************************
  testImplementation(libs.misk)
  testImplementation(libs.miskActions)
  testImplementation(libs.miskInject)
  testImplementation(libs.miskJdbc)
  testImplementation(testFixtures(libs.miskJdbc))
  testImplementation(libs.miskTesting)
  testImplementation(project(":client-misk"))
}
