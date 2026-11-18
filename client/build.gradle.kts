import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.squareup.wire")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.jakartaInject)
  api(libs.javaxInject)

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

val binaryCompat: SourceSet by sourceSets.creating

dependencies {
  "binaryCompatCompileOnly"("app.cash.backfila:client:2026.10.01.201111-ad9f619") {
    isTransitive = false
  }
  "binaryCompatImplementation"(libs.kotlinStdLib)
}

val binaryCompatCheck by tasks.registering(JavaExec::class) {
  description = "Runs code compiled against the last released client with the current client."
  group = "verification"
  classpath = binaryCompat.output + sourceSets.main.get().runtimeClasspath
  mainClass.set("app.cash.backfila.client.BinaryCompatConsumerKt")
}

tasks.named("check") {
  dependsOn(binaryCompatCheck)
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
