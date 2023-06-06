plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":client"))
  implementation(project(":backfila-embedded"))

  implementation(Dependencies.junitEngine)
  implementation(Dependencies.assertj)
}
