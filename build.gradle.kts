@file:Suppress("PropertyName", "SpellCheckingInspection")

plugins {
  kotlin("jvm") version "2.0.0"
  id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

val ktor_version: String by project
val exposed_version: String by project

group = "com.lucasalfare"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  // ktor client
  implementation("io.ktor:ktor-client-core:$ktor_version")
  implementation("io.ktor:ktor-client-cio:$ktor_version")

  // Serialization
  implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

  testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
  useJUnitPlatform()
}
kotlin {
  jvmToolchain(17)
}