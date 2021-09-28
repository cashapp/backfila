apply(plugin = "kotlin")

dependencies {
  implementation(Dependencies.aws2Dynamodb)
  implementation(Dependencies.aws2DynamodbEnhanced)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.misk)
  implementation(Dependencies.miskAws2Dynamodb)
  implementation(Dependencies.miskInject)
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
  implementation(project(":client-base"))
  // TODO: should not depend on misk
  api(project(":client-misk"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.miskAws2DynamodbTesting)
  testImplementation(Dependencies.miskTesting)

  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-misk-testing"))
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-misk-client-dynamodb-v2"
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
