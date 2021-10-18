
import nu.studer.gradle.jooq.JooqEdition

buildscript {
  dependencies {
    classpath(Dependencies.flywayGradleBuildscriptDep)
    classpath(Dependencies.mysql)
  }
}

plugins {
  id("org.flywaydb.flyway") version Versions.flywayDBPlugin
  id("nu.studer.jooq") version Versions.jooqGenPlugin
}

apply(plugin = "kotlin")

dependencies {
  implementation(Dependencies.guava)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.jooq)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.wireMoshiAdapter)

  jooqGenerator(Dependencies.mysql)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.okHttp)
  testImplementation(Dependencies.mysql)

  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-testing"))

  // ****************************************
  // For TESTING purposes only. We only want Misk for easy testing.
  // DO NOT turn these into regular dependencies.
  // ****************************************
  testImplementation(Dependencies.misk)
  testImplementation(Dependencies.miskJdbc)
  testImplementation(Dependencies.miskActions)
  testImplementation(Dependencies.miskCore)
  testImplementation(Dependencies.miskInject)
  testImplementation(Dependencies.miskJdbcTesting)
  testImplementation(Dependencies.miskTesting)
  testImplementation(project(":client-misk"))

}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-client-jooq"
}

if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")

flyway {
  url = "jdbc:mysql://localhost:3500/backfila-jooq-codegen"
  user = "root"
  password = "root"
  schemas = arrayOf("jooq")
  locations = arrayOf("filesystem:${project.projectDir}/src/test/resources/db-migrations")
  sqlMigrationPrefix = "v"
}

jooq {
  version.set(Versions.jooq)
  edition.set(JooqEdition.OSS)

  configurations {
    create("main") {
      generateSchemaSourceOnCompilation.set(false)
      jooqConfiguration.apply {
        jdbc.apply {
          driver = "com.mysql.cj.jdbc.Driver"
          url = "jdbc:mysql://localhost:3500/backfila-jooq-codegen"
          user = "root"
          password = "root"
        }
        generator.apply {
          name = "org.jooq.codegen.KotlinGenerator"
          database.apply {
            name = "org.jooq.meta.mysql.MySQLDatabase"
            inputSchema = "jooq"
            outputSchema = "jooq"
            includes = ".*"
            excludes = "(.*?FLYWAY_SCHEMA_HISTORY)|(.*?schema_version)"
            recordVersionFields = "version"
          }
          generate.apply {
            isJavaTimeTypes = true
          }
          target.apply {
            packageName = "app.cash.backfila.client.jooq.gen"
            directory   = "${project.projectDir}/src/test/generated/kotlin"
          }
        }
      }
    }
  }
}

val generateJooq by project.tasks
generateJooq.dependsOn("flywayMigrate")

sourceSets.getByName("test").java.srcDirs
  .add(File("${project.projectDir}/src/test/generated/kotlin"))