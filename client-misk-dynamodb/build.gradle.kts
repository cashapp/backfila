plugins {
  kotlin("jvm")
  `java-library`
  id("kotlin-publishing-convention")
}

dependencies {
  implementation(libs.kotlinStdLib)

  api(project(":client-dynamodb"))
}
