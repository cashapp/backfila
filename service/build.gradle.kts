import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.vanniktech.maven.publish.tasks.JavadocJar

plugins {
  id("com.gradleup.shadow")
  kotlin("jvm")
  id("com.diffplug.spotless")
  id("com.vanniktech.maven.publish.base")
  id("com.squareup.wire")
}

dependencies {
  implementation(libs.apacheCommonsLang3)
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.jacksonDatabind)
  implementation(libs.jacksonDataformatYaml)
  implementation(libs.jacksonJsr310)
  implementation(libs.jacksonKotlin)
  implementation(libs.jCommander)
  implementation(libs.jettyServer)
  implementation(libs.jettyServlet)
  implementation(libs.jettyWebsocketServlet)
  implementation(libs.jettyWebsocketServer)
  implementation(libs.kotlinStdLib)
  implementation(libs.kotlinReflection)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.kotlinxCoroutinesLogging)
  implementation(libs.kotlinxHtml)
  implementation(libs.loggingApi)
  implementation(libs.misk)
  implementation(libs.miskActions)
  implementation(libs.miskAdmin)
  implementation(libs.miskAuditClient)
  implementation(libs.miskCore)
  implementation(libs.miskGrpcReflect)
  implementation(libs.miskHibernate)
  implementation(libs.miskHotwire)
  implementation(libs.miskInject)
  implementation(libs.miskJdbc)
  implementation(libs.miskMetrics)
  implementation(libs.miskMetricsPrometheus)
  implementation(libs.miskService)
  implementation(libs.miskSlack)
  implementation(libs.miskTailwind)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(libs.openTracing)
  implementation(libs.openTracingOkHttp)
  implementation(libs.retrofit)
  implementation(libs.retrofitGuavaAdapter)
  implementation(libs.retrofitWire)
  implementation(libs.wireRuntime)
  implementation(libs.wireCompiler)
  implementation(libs.wireSchema)
  implementation(libs.wispDeployment)
  implementation(libs.miskLogging)

  implementation(project(":client"))

  testImplementation(libs.miskTesting)
  testImplementation(libs.miskHibernateTesting)
  testImplementation(testFixtures(libs.miskAuditClient))
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.junitEngine)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.assertj)
  testImplementation(libs.openTracingMock)
  testImplementation(libs.okHttpMockWebServer)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.slf4jApi)

  // Dependencies for fake development services ONLY.
  testImplementation(project(":client-base"))
  testImplementation(project(":client-misk"))
  testImplementation(project(":client-static"))
}

wire {
  protoLibrary = true
  kotlin {
    javaInterop = true
  }
}

val jar by tasks.getting(Jar::class) {
  manifest {
    attributes("Main-Class" to "app.cash.backfila.service.BackfilaDevelopmentServiceKt")
  }
  isZip64 = true
}

val shadowJar by tasks.getting(ShadowJar::class) {
  exclude("module-info.class") // https://github.com/johnrengelman/shadow/issues/352
  // https://youtrack.jetbrains.com/issue/KT-25709
  exclude("**/*.kotlin_metadata")

  mergeServiceFiles()
  isZip64 = true
  archiveClassifier.set("shaded")
}

// create a copy of the regular jar and add the classifier of unshaded so as to not break anyone without a migration
// period. This is apparently well off the paved road for the publishing plugin and gradle, so there is a lot to
// recreate by hand.
val unshadedJar by tasks.register<Jar>("unshadedJar") {
  // get the original jar files
  from(project.tasks.named<Jar>("jar").get().source)
  // don't want the one from the original jar, as it ends up in the root and this task makes another manifest anyway
  exclude("MANIFEST.MF")
  manifest {
    attributes("Main-Class" to "app.cash.backfila.service.BackfilaDevelopmentServiceKt")
  }
  description =
    "the unshaded jar, published without a classifier to be consistent with how sources and javadoc is published"
  archiveClassifier.set("unshaded")

  isZip64 = true
}
val unshadedArtifact = artifacts.add("runtimeClasspath",unshadedJar)

// we are not using the kotlin-publishing-convention here because we need to declare the unshaded artifact, but
// apparently you can't modify the MavenPublication instance once it's created. We've instead reverse engineered what
// it does and added our extra bit. Once we're in the clear about removing the unshaded jar we can switch to what
// every other subproject does for publication

tasks.register("dokkaJavadocJar", JavadocJar::class) {
  val dokkaTask = project.tasks.named("dokkaGfm")
  dependsOn(dokkaTask)
  from(dokkaTask)

}
project.extensions.getByType(JavaPluginExtension::class.java).withSourcesJar()


publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["java"])
      artifact(unshadedArtifact)
      artifact(project.tasks.named("dokkaJavadocJar"))
    }
  }
}

