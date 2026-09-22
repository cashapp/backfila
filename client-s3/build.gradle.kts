import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  // Aggregator that exposes both S3 backends. Depend on client-s3-aws-v2 directly for a classpath
  // without the AWS SDK v1.
  api(project(":client-s3-aws-v2"))
  api(project(":client-s3-aws-v1"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
