import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  id("com.github.johnrengelman.shadow")
  kotlin("jvm")
  id("com.diffplug.spotless")
  id("com.vanniktech.maven.publish.base")
}

sourceSets {
  val main by getting {
    resources.srcDir(listOf(
      "web/tabs/app/lib"
    ))
    resources.srcDir(listOf(
      "web/static/"
    ))
    resources.exclude("**/node_modules")
  }
}

dependencies {
  implementation(Dependencies.apacheCommonsLang3)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.jacksonDatabind)
  implementation(Dependencies.jacksonDataformatYaml)
  implementation(Dependencies.jacksonJsr310)
  implementation(Dependencies.jacksonKotlin)
  implementation(Dependencies.jCommander)
  implementation(Dependencies.jettyServer)
  implementation(Dependencies.jettyServlet)
  implementation(Dependencies.jettyWebsocketServlet)
  implementation(Dependencies.jettyWebsocketServer)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinxCoroutines)
  implementation(Dependencies.kotlinxCoroutinesLogging)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.metricsCore)
  implementation(Dependencies.metricsParent)
  implementation(Dependencies.misk)
  implementation(Dependencies.miskActions)
  implementation(Dependencies.miskAdmin)
  implementation(Dependencies.miskCore)
  implementation(Dependencies.miskHibernate)
  implementation(Dependencies.miskInject)
  implementation(Dependencies.miskMetrics)
  implementation(Dependencies.miskService)
  implementation(Dependencies.miskSlack)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.openTracing)
  implementation(Dependencies.openTracingOkHttp)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitGuavaAdapter)
  implementation(Dependencies.retrofitWire)
  implementation(Dependencies.tracingJaeger)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.wireCompiler)
  implementation(Dependencies.wireSchema)
  implementation(Dependencies.wispClient)
  implementation(Dependencies.wispConfig)
  implementation(Dependencies.wispDeployment)
  implementation(Dependencies.wispLogging)

  implementation(project(":client"))

  testImplementation(Dependencies.miskTesting)
  testImplementation(Dependencies.miskHibernateTesting)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.kotlinxCoroutinesTest)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.openTracingMock)
  testImplementation(Dependencies.okHttpMockWebServer)
  testImplementation(Dependencies.slf4jSimple)
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
