plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  implementation(Dependencies.apacheCommonsLang3)
  implementation(Dependencies.guava)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.guice)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitMock)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.retrofitWire)
  implementation(Dependencies.okio)
  implementation(Dependencies.kotlinStdLib)
  implementation(Dependencies.wireMoshiAdapter)
  implementation(Dependencies.wispLogging)

  // "client" is an implementation not an api dependency because client implementations MUST
  // expose "client" as an explicit api dependency so that customers of that client have access to
  // the correct classes.
  //
  // These base classes should be an implementation for the client implementations as well making
  // base classes accessible to the client implementations but not the client customers.
  implementation(project(":client"))

  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinReflection)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(project(":backfila-embedded"))

  // ****************************************
  // For TESTING purposes only. We only want Misk for easy testing.
  // DO NOT turn these into regular dependencies.
  // ****************************************
  /* TEST ONLY */ testImplementation(Dependencies.misk)
  /* TEST ONLY */ testImplementation(Dependencies.miskTesting)
  /* TEST ONLY */ testImplementation(project(":client-misk"))
  // ****************************************
  // Can I make it any more obvious?
  // ****************************************
}
