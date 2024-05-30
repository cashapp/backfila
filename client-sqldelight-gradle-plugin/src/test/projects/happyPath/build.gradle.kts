buildscript {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
  }
  dependencies {
    classpath("app.cash.backfila:client-sqldelight-gradle-plugin:${project.property("backfilaVersion")}")
    // classpath(libs.kotlin.gradle.plugin)
  }
}

allprojects {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
    google()
  }
}