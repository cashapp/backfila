plugins {
  kotlin("jvm")
  `java-library`
  id("kotlin-publishing-convention")
}

dependencies {
  implementation(libs.awsS3)
  implementation(libs.aws2S3)
  implementation(libs.guava)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.wireRuntime)
  implementation(libs.guice)
  implementation(libs.okHttp)
  implementation(libs.retrofit)
  implementation(libs.retrofitMock)
  implementation(libs.retrofitMoshi)
  implementation(libs.retrofitWire)
  implementation(libs.okio)
  implementation(libs.kotlinStdLib)
  implementation(libs.wireMoshiAdapter)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))
  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
  testImplementation(libs.kotlinTest)

  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-testing"))

  // ****************************************
  // For TESTING purposes only. We only want Misk for easy testing.
  // DO NOT turn these into regular dependencies.
  // ****************************************
  testImplementation(libs.misk)
  testImplementation(libs.miskActions)
  testImplementation(libs.miskInject)
  testImplementation(libs.miskTesting)
  testImplementation(project(":client-misk"))
}
