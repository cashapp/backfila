plugins {
  `java-platform`
  id("publishing-convention")
}

dependencies {
  constraints {
    project.rootProject.subprojects.forEach { subproject ->
      if (subproject.name !in listOf("bom", "client-sqldelight-test")) {
        api(subproject)
      }
    }
  }
}

extensions.configure<PublishingExtension> {
  publications.create("maven", MavenPublication::class) {
    from(project.components.getByName("javaPlatform"))
  }
}
