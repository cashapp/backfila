import com.vanniktech.maven.publish.JavadocJar
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
  implementation(libs.guice)
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinStdLib)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.retrofit)
  implementation(libs.retrofitMock)

  api(project(":client"))
  implementation(project(":client-base"))


  testImplementation(libs.assertj)
  testImplementation(libs.kotlinTest)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
