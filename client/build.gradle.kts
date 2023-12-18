import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.squareup.wire")
  id("com.vanniktech.maven.publish.base")
}

sourceSets {
  val main by getting {
    java.srcDir("$buildDir/generated/source/wire/")
  }
}

dependencies {
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.guice)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.retrofitWire)
  implementation(Dependencies.okio)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.wireMoshiAdapter)
  implementation(Dependencies.wireGrpcClient)
}

wire {
  protoLibrary = true
  sourcePath {
    srcDir("src/main/proto")
  }
  java {
  }
}

tasks.named("kotlinSourcesJar") {
  dependsOn("generateMainProtos")
}

tasks.named("dokkaGfm") {
  dependsOn("generateMainProtos")
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
