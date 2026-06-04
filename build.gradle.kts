import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }

  dependencies {
    classpath(libs.kotlinGradlePlugin)
    classpath(libs.kotlinAllOpenPlugin)
    classpath(libs.dokkaGradlePlugin)
    classpath(libs.mavenPublishGradlePlugin)
    classpath(libs.spotlessPlugin)
    classpath(libs.wireGradlePlugin)
    classpath(libs.buildConfigPlugin)
    classpath(libs.shadowJarPlugin)
    classpath("app.cash.backfila:client-sqldelight-gradle-plugin")
  }
}

allprojects {
  group = project.property("GROUP") as String
  version = project.findProperty("VERSION_NAME") as? String ?: "0.0-SNAPSHOT"
}

subprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "dokka-convention")
  apply(plugin = "org.jetbrains.kotlin.plugin.allopen")

  tasks.withType<KotlinCompile> {
    dependsOn("spotlessKotlinApply")
    compilerOptions {
      jvmTarget = JvmTarget.JVM_11
      freeCompilerArgs.add("-Xjdk-release=11")
    }
  }

  tasks.withType<JavaCompile> {
    options.release.set(11)
  }

  configure<AllOpenExtension> {
    annotation("javax.persistence.Entity")
    annotation("javax.persistence.Embeddable")
    annotation("javax.persistence.MappedSuperclass")
  }

  configure<SpotlessExtension> {
    kotlin {
      target("**/*.kt")
      targetExclude(
          "**/node_modules/**", 
          "**/build/**", 
          "**/.gradle/**",
      )
      ktlint(libs.versions.ktlint.get()).editorConfigOverride(
          mapOf(
              "indent_size" to "2",
              "continuation_indent_size" to "4",
              "trailing_comma_on_call_site" to "true",
              "trailing_comma_on_declaration_site" to "true",
              "ij_kotlin_allow_trailing_comma" to "true",
              "ij_kotlin_allow_trailing_comma_on_declaration_site" to "true",
              "ij_kotlin_allow_trailing_comma_on_call_site" to "true",
              "ij_kotlin_imports_layout" to "*",
              "ktlint_disabled_rules" to "argument-list-wrapping",
          )
      )
    }
  }

  repositories {
    mavenCentral()
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