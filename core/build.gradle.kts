plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.monday8am.edgelab.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appAuthRedirectScheme"] = "com.monday8am.edgelab"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

    buildFeatures { buildConfig = true }
}

dependencies {
    // Project modules - expose via api for transitive dependencies
    api(project(":data"))
    api(project(":agent")) {
        exclude(group = "com.google.ai.edge.litertlm", module = "litertlm-jvm")
    }
    api(project(":presentation")) {
        exclude(group = "com.google.ai.edge.litertlm", module = "litertlm-jvm")
    }

    // Android core
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Inference
    implementation(libs.litertlm.android)

    // Download
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)

    // OAuth
    implementation(libs.appauth)
    implementation(libs.androidx.browser)

    // Storage
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.kermit)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
