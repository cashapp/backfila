plugins {
  kotlin("jvm")
  `java-library`
  id("kotlin-publishing-convention")
}

dependencies {
  api(project(":client"))
  implementation(project(":backfila-embedded"))

  implementation(libs.junitEngine)
  implementation(libs.assertj)
}
