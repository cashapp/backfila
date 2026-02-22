import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

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
  implementation(libs.metricsCore)
  implementation(libs.metricsParent)
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
  implementation(libs.tracingJaeger)
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
  testImplementation(project(":client-s3"))
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
  archiveClassifier.set("unshaded")
}

val shadowJar by tasks.getting(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
  exclude("module-info.class") // https://github.com/johnrengelman/shadow/issues/352
  // https://youtrack.jetbrains.com/issue/KT-25709
  exclude("**/*.kotlin_metadata")

  mergeServiceFiles()
  isZip64 = true
  archiveClassifier.set("shaded")
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
