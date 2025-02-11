import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
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
  implementation(libs.retrofit)
  implementation(libs.retrofitMock)
  implementation(libs.retrofitMoshi)
  implementation(libs.retrofitWire)
  implementation(libs.okio)
  implementation(libs.kotlinStdLib)
  implementation(libs.wireMoshiAdapter)
  implementation(libs.wispLogging)

  // "client" is an implementation not an api dependency because client implementations MUST
  // expose "client" as an explicit api dependency so that customers of that client have access to
  // the correct classes.
  //
  // These base classes should be an implementation for the client implementations as well making
  // base classes accessible to the client implementations but not the client customers.
  implementation(project(":client"))

  testImplementation(libs.junitEngine)
  testImplementation(libs.assertj)
  testImplementation(libs.kotlinReflection)
  testImplementation(libs.kotlinTest)
  testImplementation(project(":backfila-embedded"))

  // ****************************************
  // For TESTING purposes only. We only want Misk for easy testing.
  // DO NOT turn these into regular dependencies.
  // ****************************************
  /* TEST ONLY */ testImplementation(libs.misk)
  /* TEST ONLY */ testImplementation(libs.miskTesting)
  /* TEST ONLY */ testImplementation(project(":client-misk"))
  // ****************************************
  // Can I make it any more obvious?
  // ****************************************
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
