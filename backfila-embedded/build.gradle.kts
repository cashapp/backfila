apply(plugin = "kotlin")

dependencies {
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitMock)
  implementation(Dependencies.miskInject)

  api(project(":client"))
  implementation(project(":client-base"))


  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-embedded"
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
