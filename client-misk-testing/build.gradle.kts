apply(plugin = "kotlin")

dependencies {
  api(project(":client-misk"))

  implementation(Dependencies.misk)
  implementation(Dependencies.miskTesting)
  implementation(Dependencies.junitEngine)
  implementation(Dependencies.assertj)
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-client-misk-testing"
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
