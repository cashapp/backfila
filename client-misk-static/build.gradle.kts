apply(plugin = "kotlin")

dependencies {
  implementation(Dependencies.guava)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.guice)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitMock)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.retrofitWire)
  implementation(Dependencies.okio)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.wireMoshiAdapter)

  api(project(":client"))
  api(project(":client-misk"))
  implementation(project(":client-base"))
  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-misk-testing"))

  implementation(Dependencies.misk)
  implementation(Dependencies.miskInject)
  implementation(Dependencies.miskActions)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.miskTesting)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.kotlinTest)
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-misk-client-static"
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
