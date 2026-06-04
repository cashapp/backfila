import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("publishing-convention")
  id("dokka-convention")
  kotlin("jvm")
}
mavenPublishing {

  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationMarkdown"))
  )
}