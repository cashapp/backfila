apply(plugin = "kotlin")

dependencies {
  implementation(Dependencies.kotlinStdLib)

  api(project(":client-jooq"))
}

val jar by tasks.getting(Jar::class) {
  archiveBaseName.set("backfila-client-misk-jooq")
}
if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")