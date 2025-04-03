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
  implementation(libs.okio)
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)
  implementation(libs.loggingApi)
  implementation(libs.wireMoshiAdapter)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))

  implementation(libs.misk)
  implementation(libs.miskActions)
  implementation(libs.miskCore)
  implementation(libs.miskInject)
  implementation(libs.wispLogging)
  api(libs.miskHibernate)

  testImplementation(libs.assertj)
  testImplementation(libs.miskTesting)
  testImplementation(testFixtures(libs.miskJdbc))
  testImplementation(libs.miskHibernateTesting)
  testImplementation(testFixtures(libs.miskVitess))
  testImplementation(project(":client-misk"))
  testImplementation(libs.kotlinTest)
  testImplementation(libs.junitEngine)
  testImplementation(libs.okHttp)

  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-static"))
  testImplementation(project(":client-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
