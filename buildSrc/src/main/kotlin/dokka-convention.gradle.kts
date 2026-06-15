import org.jetbrains.dokka.gradle.formats.DokkaFormatPlugin
import org.jetbrains.dokka.gradle.internal.InternalDokkaGradlePluginApi

plugins {
  id("org.jetbrains.dokka")
}

// Declares Markdown Gradle plugin
@OptIn(InternalDokkaGradlePluginApi::class)
abstract class DokkaMarkdownPlugin : DokkaFormatPlugin(formatName = "markdown") {
  override fun DokkaFormatPlugin.DokkaFormatPluginContext.configure() {
    project.dependencies {
      // Sets up current project generation
      dokkaPlugin(dokka("gfm-plugin"))

      // Sets up multi-project generation
      formatDependencies.dokkaPublicationPluginClasspathApiOnly.dependencies.addLater(
        dokka("gfm-template-processing-plugin")
      )
    }
  }
}
// Applies the plugin
apply<DokkaMarkdownPlugin>()


dokka {
  dokkaSourceSets.configureEach {
    reportUndocumented.set(false)
    skipDeprecated.set(true)
    jdkVersion.set(11)
  }
}
