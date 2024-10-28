import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  id("java-gradle-plugin")
  kotlin("jvm")
  id("com.github.gmazzo.buildconfig")
  id("com.vanniktech.maven.publish.base")
}

// This module is used in two places:
//
// - In the root project, so we can:
//    - publish the plugin
//    - run the plugin's own unit test
//
// - In build-support project
//    - In this project, we consume it internally.
//
// We only want to publish when it's being built in the root project.
if (rootProject.name == "backfila") {
  configure<MavenPublishBaseExtension> {
    configure(
      GradlePlugin(
        javadocJar = JavadocJar.Empty()
      )
    )
  }
} else {
  // Move the build directory when included in build-support so as to not poison the real build.
  // If we don't there's a chance incorrect build config values (configured below) will be used.
  layout.buildDirectory.set(File(rootDir, "build/internal"))
}

dependencies {
  implementation(libs.kotlinGradlePlugin)
  implementation(libs.kotlinPoet)
  implementation(libs.sqldelightGradlePlugin)

  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
  testImplementation(libs.kotlinTest)
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
    useJUnitPlatform()
    // The test in 'src/test/projects/android' needs Java 17+.
    javaLauncher.set(
      project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(17))
      }
    )
    systemProperty("backfilaVersion", rootProject.findProperty("VERSION_NAME") ?: "0.0-SNAPSHOT")

    if (rootProject.name == "backfila") {
      dependsOn(":client:publishAllPublicationsToTestMavenRepository")
      dependsOn(":client-base:publishAllPublicationsToTestMavenRepository")
      dependsOn(":client-sqldelight:publishAllPublicationsToTestMavenRepository")
      dependsOn(":client-sqldelight-gradle-plugin:publishAllPublicationsToTestMavenRepository")
    }
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
