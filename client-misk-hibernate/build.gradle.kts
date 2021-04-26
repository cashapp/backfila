apply(plugin = "kotlin")

dependencies {
  implementation(Dependencies.guava)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.wireMoshiAdapter)

  api(project(":client-misk"))
  testApi(project(":client-misk-testing"))

  implementation(Dependencies.misk)
  implementation(Dependencies.miskActions)
  implementation(Dependencies.miskCore)
  implementation(Dependencies.miskInject)
  api(Dependencies.miskHibernate)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.miskTesting)
  testImplementation(Dependencies.miskHibernateTesting)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.okHttp)
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-client-misk-hibernate"
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