apply(plugin = "kotlin")

dependencies {
  implementation(Dependencies.guava)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.gcpSpanner)
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.wireMoshiAdapter)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))

  implementation(Dependencies.misk)
  implementation(Dependencies.miskActions)
  implementation(Dependencies.miskCore)
  implementation(Dependencies.miskGcp)
  implementation(Dependencies.miskInject)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.miskTesting)
  testImplementation(Dependencies.miskGcpTesting)
  testImplementation(project(":client-misk"))
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.okHttp)

  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-testing"))
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-client-misk-spanner"
}

if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")