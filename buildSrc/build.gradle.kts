plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  implementation(libs.dokkaGradlePlugin)
  implementation(libs.kotlinGradlePlugin)
}