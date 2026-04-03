plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":data"))
    api(libs.koog.agents) {
        // Exclude to avoid duplicate classes with kotlin-sdk-client
        exclude(group = "io.modelcontextprotocol", module = "kotlin-sdk-core-jvm")
    }
    @Suppress("UnstableApiUsage") implementation(libs.litertlm.jvm)
    api(libs.kermit)
    // Note: LiteRT-LM is only used in :app module (Android implementation)
    // :agent module is pure Kotlin/JVM and uses interfaces only
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
