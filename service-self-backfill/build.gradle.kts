import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.diffplug.spotless")
  id("com.vanniktech.maven.publish.base")
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
  implementation(libs.loggingApi)
  implementation(libs.metricsCore)
  implementation(libs.metricsParent)
  implementation(libs.misk)
  implementation(libs.miskActions)
  implementation(libs.miskAdmin)
  implementation(libs.miskCore)
  implementation(libs.miskHibernate)
  implementation(libs.miskInject)
  implementation(libs.miskMetrics)
  implementation(libs.miskService)
  implementation(libs.miskSlack)
  implementation(libs.moshiCore)
  implementation(libs.moshiKotlin)
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
  implementation(libs.wispClient)
  implementation(libs.wispConfig)
  implementation(libs.wispDeployment)
  implementation(libs.wispLogging)

  implementation(project(":client"))
  implementation(project(":client-base"))
  implementation(project(":client-misk"))
  implementation(project(":client-misk-hibernate"))
  implementation(project(":service"))

  testImplementation(libs.miskTesting)
  testImplementation(libs.miskHibernateTesting)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.junitEngine)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.assertj)
  testImplementation(libs.openTracingMock)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.slf4jApi)

  testImplementation(project(":backfila-embedded"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
