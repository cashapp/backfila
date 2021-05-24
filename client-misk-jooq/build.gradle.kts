import nu.studer.gradle.jooq.JooqEdition
import org.jetbrains.kotlin.backend.common.push
import org.jooq.meta.jaxb.ForcedType

buildscript {
  dependencies {
    classpath("gradle.plugin.com.boxfuse.client:flyway-release:5.0.2")
    classpath(Dependencies.mysql)
  }
}

plugins {
  id("org.flywaydb.flyway") version "5.2.4"
  id("nu.studer.jooq") version "5.2"
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

  api(project(":client-misk"))
  testApi(project(":client-misk-testing"))

  implementation(Dependencies.misk)
  implementation(Dependencies.miskJdbc)
  implementation(Dependencies.miskActions)
  implementation(Dependencies.miskCore)
  implementation(Dependencies.miskInject)

  testImplementation(Dependencies.miskJdbcTesting)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.miskTesting)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.okHttp)
  testImplementation(Dependencies.mysql)

  jooqGenerator(Dependencies.mysql)
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-client-misk-jooq"
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
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
  version.set("3.14.8")
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
            packageName = "app.cash.backfila.client.misk.jooq.gen"
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