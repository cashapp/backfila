apply(plugin = "kotlin")

dependencies {
  implementation(Dependencies.kotlinStdLib)

  api(project(":client-static"))
}

val jar by tasks.getting(Jar::class) {
  archiveBaseName.set("backfila-misk-client-static")
}

if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
