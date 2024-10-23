import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("java-gradle-plugin")
  kotlin("jvm")
  `java-library`
  id("com.github.gmazzo.buildconfig")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  implementation(libs.guava)
  implementation(libs.kotlinGradlePlugin)
  implementation(libs.kotlinPoet)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.wireRuntime)
  implementation(libs.guice)
  implementation(libs.kotlinStdLib)
  implementation(libs.sqldelightJdbcDriver)
  implementation(libs.sqldelightGradlePlugin)
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(libs.retrofit)
  implementation(libs.retrofitMock)
  implementation(libs.retrofitMoshi)
  implementation(libs.retrofitWire)
  implementation(libs.wireMoshiAdapter)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))


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

tasks.withType<KotlinCompile> {
  dependsOn("spotlessKotlinApply")
  kotlinOptions {
    jvmTarget = "17"
  }
}

tasks.withType<JavaCompile> {
  sourceCompatibility = JavaVersion.VERSION_17.toString()
  targetCompatibility = JavaVersion.VERSION_17.toString()
}

gradlePlugin {
  plugins {
    create("backfila-client-sqldelight") {
      id = "app.cash.backfila.client.sqldelight"
      displayName = "backfilaClientSqlDelight"
      description = "Gradle plugin to generate sqldelight files for Backfila backfills."
      implementationClass = "app.cash.backfila.client.sqldelight.plugin.BackfilaSqlDelightGradlePlugin"
    }
  }
}

tasks {
  test {
    // The test in 'src/test/projects/android' needs Java 17+.
    javaLauncher.set(
      project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
      }
    )
    systemProperty("backfilaVersion", rootProject.findProperty("VERSION_NAME") ?: "0.0-SNAPSHOT")
    dependsOn(":client:publishAllPublicationsToTestMavenRepository")
    dependsOn(":client-base:publishAllPublicationsToTestMavenRepository")
    dependsOn(":client-sqldelight:publishAllPublicationsToTestMavenRepository")
    dependsOn(":client-sqldelight-gradle-plugin:publishAllPublicationsToTestMavenRepository")
  }
}

buildConfig {
  useKotlinOutput {
    internalVisibility = true
    topLevelConstants = true
  }

  packageName("app.cash.backfila.client.sqldelight.plugin")
  buildConfigField("String", "VERSION", "\"${project.version}\"")
}

configure<MavenPublishBaseExtension> {
  configure(
    GradlePlugin(
      javadocJar = JavadocJar.Empty()
    )
  )
}