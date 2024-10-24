buildscript {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
  dependencies {
    classpath(libs.kotlinGradlePlugin)
    classpath(libs.mavenPublishGradlePlugin)
    classpath(libs.buildConfigPlugin)
  }
}

allprojects {
  repositories {
    mavenCentral()
  }
}


