plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}
dependencies {
  implementation(Dependencies.kotlinStdLib)

  api(project(":client-static"))
}
