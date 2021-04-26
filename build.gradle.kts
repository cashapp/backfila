import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    mavenCentral()
    jcenter()
  }

  dependencies {
    classpath(Dependencies.kotlinGradlePlugin)
    classpath(Dependencies.kotlinAllOpenPlugin)
    classpath(Dependencies.mavenPublishGradlePlugin)
    classpath(Dependencies.shadowJarPlugin)
    classpath(Dependencies.spotlessPlugin)
    classpath(Dependencies.wireGradlePlugin)
  }
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "kotlin")
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "org.jetbrains.kotlin.plugin.allopen")

  val compileKotlin by tasks.getting(KotlinCompile::class) {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    dependsOn("spotlessKotlinApply")
  }

  val compileTestKotlin by tasks.getting(KotlinCompile::class) {
    kotlinOptions {
      jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
  }

  tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
  }

  configure<AllOpenExtension> {
    annotation("javax.persistence.Entity")
    annotation("javax.persistence.Embeddable")
    annotation("javax.persistence.MappedSuperclass")
  }

  configure<SpotlessExtension> {
    kotlin {
      target("**/*.kt")
      ktlint(Dependencies.ktlintVersion).userData(
              mapOf(
                      "indent_size" to "2",
                      "continuation_indent_size" to "4",
                      "disabled_rules" to "import-ordering"
              ))
    }
  }

  repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
  }

  tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
      events("started", "passed", "skipped", "failed")
      exceptionFormat = TestExceptionFormat.FULL
      showExceptions = true
      showStackTraces = true
    }
  }

  // SLF4J uses the classpath to decide which logger to use! Banish the Log4J to prevent this:
  // org.apache.logging.slf4j.Log4jLogger cannot be cast to class ch.qos.logback.classic.Logger
  configurations.all {
    exclude(group = "org.apache.logging.log4j", module = "log4j-slf4j-impl")
  }

  // Workaround the Gradle bug resolving multiplatform dependencies.
  // https://github.com/square/okio/issues/647
  configurations.all {
    if (name.contains("kapt") || name.contains("wire") || name.contains("proto")) {
      attributes.attribute(Usage.USAGE_ATTRIBUTE, this@subprojects.objects.named(Usage::class, Usage.JAVA_RUNTIME))
    }
  }
}
