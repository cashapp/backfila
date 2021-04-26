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
  implementation(Dependencies.guava)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.guice)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.retrofitWire)
  implementation(Dependencies.okio)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.wireMoshiAdapter)

  testImplementation(Dependencies.junitEngine)
}

val jar by tasks.getting(Jar::class) {
  baseName = "backfila-client"
}

wire {
  protoLibrary = true
  sourcePath {
    srcDir("src/main/proto")
  }
  java {
  }
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
