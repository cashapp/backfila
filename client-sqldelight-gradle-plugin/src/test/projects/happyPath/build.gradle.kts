buildscript {
  repositories {
    maven {
      url = file("$rootDir/../../../../../build/testMaven").toURI()
    }
    mavenCentral()
  }
  dependencies {
    classpath("app.cash.backfila:client-sqldelight-gradle-plugin:${project.property("backfilaVersion")}")
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