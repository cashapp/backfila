import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  implementation(libs.guava)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.wireRuntime)
  implementation(libs.guice)
  implementation(libs.kotlinStdLib)
  implementation(libs.sqldelightJdbcDriver)
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(libs.retrofit)
  implementation(libs.retrofitMock)
  implementation(libs.retrofitMoshi)
  implementation(libs.retrofitWire)
  implementation(libs.wireMoshiAdapter)

  // Misk dependencies for Vitess support
  implementation(libs.miskJdbc)
  implementation(libs.miskVitess)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitEngine)
  testImplementation(libs.kotlinTest)

  testImplementation(project(":client-sqldelight-test"))  // Test fixtures
  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-testing"))
  testImplementation(project(":client-misk"))

  // Misk testing framework (for @MiskTest)
  testImplementation(libs.misk)
  testImplementation(libs.miskActions)
  testImplementation(libs.miskInject)
  testImplementation(libs.miskJdbc)
  testImplementation(testFixtures(libs.miskJdbc))
  testImplementation(libs.miskVitess)
  testImplementation(testFixtures(libs.miskVitess))
  testImplementation(libs.miskTesting)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
