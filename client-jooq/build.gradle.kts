import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import nu.studer.gradle.jooq.JooqEdition
import nu.studer.gradle.jooq.JooqGenerate

buildscript {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
  dependencies {
    classpath(libs.mysql)
  }
}

plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-library`
  id("org.flywaydb.flyway") version "7.15.0"
  id("nu.studer.jooq") version "8.1"
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  implementation(libs.guava)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.wireRuntime)
  implementation(libs.guice)
  implementation(libs.okio)
  implementation(libs.jooq)
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)
  implementation(libs.loggingApi)
  implementation(libs.wireMoshiAdapter)

  jooqGenerator(libs.mysql)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))

  testImplementation(libs.assertj)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.junitEngine)
  testImplementation(libs.junitParams)
  testImplementation(libs.okHttp)
  testImplementation(libs.mysql)
  testImplementation(libs.wispLogging)

  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-testing"))

  // ****************************************
  // For TESTING purposes only. We only want Misk for easy testing.
  // DO NOT turn these into regular dependencies.
  // ****************************************
  testImplementation(libs.misk)
  testImplementation(libs.miskJdbc)
  testImplementation(libs.miskActions)
  testImplementation(libs.miskCore)
  testImplementation(libs.miskInject)
  testImplementation(testFixtures(libs.miskJdbc))
  testImplementation(libs.miskTesting)
  testImplementation(project(":client-misk"))

}

flyway {
  url = "jdbc:mysql://localhost:3500/backfila-jooq-codegen"
  user = "root"
  password = "root"
  schemas = arrayOf("jooq")
  locations = arrayOf("filesystem:${project.projectDir}/src/test/resources/db-migrations")
  sqlMigrationPrefix = "v"
}

jooq {
  version.set(libs.versions.jooq)
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

sourceSets.getByName("test").java.srcDirs
  .add(File("${project.projectDir}/src/test/generated/kotlin"))

tasks {
  withType<JooqGenerate> {
    dependsOn(flywayMigrate)
  }
}

tasks.named<JooqGenerate>("generateJooq") { allInputsDeclared.set(true) }

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
