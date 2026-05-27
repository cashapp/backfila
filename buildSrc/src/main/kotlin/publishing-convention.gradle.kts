plugins {
  id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
  publishToMavenCentral(automaticRelease = true)
  signAllPublications()
  pom {
    description.set("Backfila is a service that manages backfill state, calling into other services to do batched work.")
    name.set(project.name)
    url.set("https://github.com/cashapp/backfila/")
    licenses {
      license {
        name.set("The Apache Software License, Version 2.0")
        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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
publishing {
  // For the Gradle plugin's tests.
  repositories {
    maven {
      name = "testMaven"
      url = rootProject.layout.buildDirectory.dir("testMaven").get().asFile.toURI()
    }
  }
}