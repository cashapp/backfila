apply(plugin = "kotlin")

dependencies {
  implementation(Dependencies.aws2Dynamodb)
  implementation(Dependencies.aws2DynamodbEnhanced)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitMock)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.retrofitWire)
  implementation(Dependencies.wireMoshiAdapter)
  implementation(Dependencies.wireRuntime)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.aws2Dynamodb)
  testImplementation(Dependencies.aws2DynamodbEnhanced)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.kotlinTest)

  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-testing"))

  // ****************************************
  // For TESTING purposes only. We only want Misk for easy testing.
  // DO NOT turn these into regular dependencies.
  // ****************************************
  testImplementation(Dependencies.misk)
  testImplementation(Dependencies.miskAws2Dynamodb)
  testImplementation(Dependencies.miskAws2DynamodbTesting)
  testImplementation(Dependencies.miskInject)
  testImplementation(Dependencies.miskTesting)
  testImplementation(project(":client-misk"))
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-client-dynamodb-v2"
}

if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
