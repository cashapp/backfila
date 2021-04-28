apply(plugin = "kotlin")
apply(plugin = "com.github.johnrengelman.shadow")
apply(plugin = "com.diffplug.spotless")

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
  implementation(Dependencies.guiceMultibindings)
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
}

val jar by tasks.getting(Jar::class) {
  manifest {
    attributes("Main-Class" to "app.cash.backfila.service.BackfilaDevelopmentServiceKt")
  }
  isZip64 = true
  classifier = "unshaded"
}

val shadowJar by tasks.getting(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
  exclude("module-info.class") // https://github.com/johnrengelman/shadow/issues/352
  mergeServiceFiles()
  isZip64 = true
  classifier = null
}

if (rootProject.file("hooks.gradle").exists()) {
  apply(from = rootProject.file("hooks.gradle"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
