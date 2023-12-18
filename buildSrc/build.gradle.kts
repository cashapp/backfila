plugins {
  kotlin("jvm") version "1.9.0"
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("com.squareup:kotlinpoet:1.13.2")
  implementation(platform("com.squareup.wire:wire-bom:4.9.3"))
  implementation("com.squareup.wire:wire-schema")
  implementation("com.squareup.wire:wire-grpc-client")
  implementation("com.squareup.wire:wire-kotlin-generator")
}
