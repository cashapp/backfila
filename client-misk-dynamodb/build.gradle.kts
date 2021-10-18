apply(plugin = "kotlin")

dependencies {
  implementation(Dependencies.kotlinStdLib)

  api(project(":client-dynamodb"))
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-misk-client-dynamodb"
}

if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
