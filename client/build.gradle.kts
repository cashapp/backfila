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
    java.srcDir(layout.buildDirectory.dir("generated/source/wire"))
  }
}

dependencies {
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.wireGrpcClient)
  implementation(libs.wireRuntime)
  implementation(libs.guice)
  implementation(libs.retrofit)
  implementation(libs.retrofitMoshi)
  implementation(libs.retrofitWire)
  implementation(libs.okio)
  implementation(libs.kotlinStdLib)
  implementation(libs.wireMoshiAdapter)
}

wire {
  protoLibrary = true
  sourcePath {
    srcDir("src/main/proto")
  }
  kotlin {
    includes = listOf(
      "app.cash.backfila.protos.clientservice.BackfilaClientService",
    )
    rpcRole = "client"
    javaInterop = true
    exclusive = true
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
