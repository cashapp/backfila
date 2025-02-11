import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  implementation(libs.aws2Dynamodb)
  implementation(libs.aws2DynamodbEnhanced)
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(libs.retrofit)
  implementation(libs.retrofitMock)
  implementation(libs.retrofitMoshi)
  implementation(libs.retrofitWire)
  implementation(libs.wireMoshiAdapter)
  implementation(libs.wireRuntime)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))

  testImplementation(libs.assertj)
  testImplementation(libs.aws2Dynamodb)
  testImplementation(libs.aws2DynamodbEnhanced)
  testImplementation(libs.junitEngine)
  testImplementation(libs.kotlinTest)

  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-testing"))

  if (org.apache.tools.ant.taskdefs.condition.Os.isArch("aarch64")) {
    // Without this, we can't compile on Apple Silicon currently. This is likely not necessary to
    // have longterm, so we should remove it when platform fixes things across Square.
    testImplementation("io.github.ganadist.sqlite4java:libsqlite4java-osx-aarch64:1.0.392")
  }

  // ****************************************
  // For TESTING purposes only. We only want Misk for easy testing.
  // DO NOT turn these into regular dependencies.
  // ****************************************
  testImplementation(libs.misk)
  testImplementation(libs.miskAws2Dynamodb)
  testImplementation(testFixtures(libs.miskAws2Dynamodb))
  testImplementation(libs.miskInject)
  testImplementation(libs.miskTesting)
  testImplementation(project(":client-misk"))
  // Required until DynamoDBLocal is built with antlr >4.11 which wisp-config pulls in
  testImplementation("org.antlr:antlr4-runtime:4.9.3") {
    version {
      strictly("4.9.3")
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
