plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":data"))
    @Suppress("UnstableApiUsage") implementation(libs.litertlm.jvm)
    api(libs.kermit)
    // Note: LiteRT-LM is only used in :app module (Android implementation)
    // :agent module is pure Kotlin/JVM and uses interfaces only
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
