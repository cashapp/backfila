import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  `java-library`
  id("com.diffplug.spotless")
  id("com.vanniktech.maven.publish.base")
  id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
  api(libs.miskInject)
  api(project(":service"))

  implementation(libs.guice)
  implementation(libs.jakartaInject)
  implementation(libs.kotlinStdLib)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.kotlinxSerializationJson)
  implementation(libs.misk)
  implementation(libs.miskActions)
  implementation(libs.miskMcp)

  testImplementation(testFixtures(libs.miskMcp))
  testImplementation(libs.miskAdmin)
  testImplementation(libs.miskAuditClient)
  testImplementation(testFixtures(libs.miskAuditClient))
  testImplementation(libs.miskTesting)
  testImplementation(libs.miskHibernate)
  testImplementation(libs.miskHibernateTesting)
  testImplementation(libs.miskJdbc)
  testImplementation(libs.miskSlack)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.assertj)
  testImplementation(libs.okHttp)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.slf4jApi)
  testImplementation(libs.wispDeployment)

  testImplementation(project(":client"))
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    optIn.add("misk.annotation.ExperimentalMiskApi")
  }
}

tasks.register<JavaExec>("runDevelopmentService") {
  description = "Runs Backfila locally with the MCP server enabled at http://localhost:8080/mcp."
  group = "application"
  classpath = sourceSets["test"].runtimeClasspath
  mainClass.set("app.cash.backfila.mcp.development.BackfilaMcpDevelopmentServiceKt")
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm")),
  )
}
