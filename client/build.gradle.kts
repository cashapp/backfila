import com.vanniktech.maven.publish.tasks.SourcesJar

plugins {
  id("com.squareup.wire")
}
apply(plugin = "kotlin")

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
}

val jar by tasks.getting(Jar::class) {
  archiveBaseName.set("backfila-client")
}

wire {
  protoLibrary = true
  sourcePath {
    srcDir("src/main/proto")
  }
  java {
  }
}

if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")

// Prevent proto source files from conflicting
afterEvaluate {
  tasks.withType<SourcesJar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  }
}
