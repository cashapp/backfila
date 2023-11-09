import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  implementation(Dependencies.aws2Dynamodb)
  implementation(Dependencies.aws2DynamodbEnhanced)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitMock)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.retrofitWire)
  implementation(Dependencies.wireMoshiAdapter)
  implementation(Dependencies.wireRuntime)

  api(project(":client"))
  // We do not want to leak client-base implementation details to customers.
  implementation(project(":client-base"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.aws2Dynamodb)
  testImplementation(Dependencies.aws2DynamodbEnhanced)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.kotlinTest)

  if (org.apache.tools.ant.taskdefs.condition.Os.isArch("aarch64")) {
    // Without this, we can't compile on Apple Silicon currently. This is likely not necessary to
    // have longterm, so we should remove it when platform fixes things across Square.
    testImplementation("io.github.ganadist.sqlite4java:libsqlite4java-osx-aarch64:1.0.392")
  }
  testImplementation(project(":backfila-embedded"))
  testImplementation(project(":client-testing"))

  // ****************************************
  // For TESTING purposes only. We only want Misk for easy testing.
  // DO NOT turn these into regular dependencies.
  // ****************************************
  testImplementation(Dependencies.misk)
  testImplementation(Dependencies.miskAws2Dynamodb)
  testImplementation(Dependencies.miskAws2DynamodbTesting)
  testImplementation(Dependencies.miskInject)
  testImplementation(Dependencies.miskTesting)
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
