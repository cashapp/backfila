rootProject.name = "build-support"

include(":client-sqldelight-gradle-plugin")
project(":client-sqldelight-gradle-plugin").projectDir = File("../client-sqldelight-gradle-plugin")

dependencyResolutionManagement {
  versionCatalogs {
    create("libs") {
      from(files("../gradle/libs.versions.toml"))
    }
  }
}
