apply(plugin = "kotlin")

dependencies {
  api(project(":client"))
  implementation(project(":backfila-embedded"))

  implementation(Dependencies.junitEngine)
  implementation(Dependencies.assertj)
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-client-testing"
}

if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
