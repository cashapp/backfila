import com.diffplug.gradle.spotless.SpotlessExtension
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.allopen.gradle.AllOpenExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }

  dependencies {
    classpath(Dependencies.kotlinGradlePlugin)
    classpath(Dependencies.kotlinAllOpenPlugin)
    classpath(Dependencies.dokkaGradlePlugin)
    classpath(Dependencies.mavenPublishGradlePlugin)
    classpath(Dependencies.spotlessPlugin)
    classpath(Dependencies.wireGradlePlugin)
    classpath(Dependencies.shadowJarPlugin)
  }
}

apply(plugin = "com.vanniktech.maven.publish.base")

allprojects {
  group = project.property("GROUP") as String
  version = project.findProperty("VERSION_NAME") as? String ?: "0.0-SNAPSHOT"
}

subprojects {
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "org.jetbrains.dokka")
  apply(plugin = "org.jetbrains.kotlin.plugin.allopen")

  tasks.withType<KotlinCompile> {
  //   dependsOn("spotlessKotlinApply")
    kotlinOptions {
      jvmTarget = "11"
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
      ktlint(Versions.ktlint).editorConfigOverride(
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

    mavenLocal {
      content {
        includeGroup("com.squareup.misk")
        includeGroup("app.cash.wisp")
      }
    }
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

  // We have to set the dokka configuration after evaluation since the com.vanniktech.maven.publish
  // plugin overwrites our dokka configuration on projects where it's applied.
  afterEvaluate {
    tasks.withType(DokkaTask::class).configureEach {
      dokkaSourceSets.configureEach {
        reportUndocumented.set(false)
        skipDeprecated.set(true)
        jdkVersion.set(8)
        if (name == "dokkaGfm") {
          outputDirectory.set(project.file("$rootDir/docs/0.x"))
        }
      }
    }
  }
}


allprojects {
  plugins.withId("com.vanniktech.maven.publish.base") {
    configure<MavenPublishBaseExtension> {
      publishToMavenCentral(SonatypeHost.DEFAULT, automaticRelease = true)
      signAllPublications()
      pom {
        description.set("Backfila is a service that manages backfill state, calling into other services to do batched work.")
        name.set(project.name)
        url.set("https://github.com/cashapp/backfila/")
        licenses {
          license {
            name.set("The Apache Software License, Version 2.0")
            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
            distribution.set("repo")
          }
        }
        scm {
          url.set("https://github.com/cashapp/backfila/")
          connection.set("scm:git:git://github.com/cashapp/backfila.git")
          developerConnection.set("scm:git:ssh://git@github.com/cashapp/backfila.git")
        }
        developers {
          developer {
            id.set("square")
            name.set("Square, Inc.")
          }
        }
      }
    }
  }
}