plugins {
  kotlin("jvm")
  `java-library`
  id("kotlin-publishing-convention")
}

dependencies {
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.retrofit)
  implementation(libs.retrofitMock)

  api(project(":client"))
  implementation(project(":client-base"))


  testImplementation(libs.assertj)
  testImplementation(libs.kotlinTest)
}
