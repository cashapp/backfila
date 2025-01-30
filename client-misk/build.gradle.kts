import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  id("org.jetbrains.kotlin.jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  implementation(libs.apacheCommonsLang3)
  implementation(libs.guava)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.wireRuntime)
  implementation(libs.guice)
  implementation(libs.okHttp)
  implementation(libs.retrofit)
  implementation(libs.retrofitMock)
  implementation(libs.retrofitMoshi)
  implementation(libs.retrofitWire)
  implementation(libs.okio)
  implementation(libs.kotlinStdLib)
  implementation(libs.wireMoshiAdapter)
  implementation(libs.slf4jApi)
  implementation(libs.loggingApi)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))

  implementation(libs.misk)
  implementation(libs.miskActions)
  implementation(libs.miskCore)
  implementation(libs.miskInject)
  implementation(libs.miskService)
  implementation(libs.wispLogging)
  implementation(libs.wispMoshi)

  testImplementation(libs.assertj)
  testImplementation(libs.miskTesting)
  testImplementation(libs.junitEngine)

  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
