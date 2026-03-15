import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    // Firebase plugins - commented out for now (requires google-services.json with edgelab package)
    // alias(libs.plugins.google.services)
    // alias(libs.plugins.firebase.crashlytics)
}

// Load upload keystore properties from file or environment variables
// With Google Play App Signing, this is the upload key (not the app signing key)
val keystorePropertiesFile = rootProject.file("signing/explorer-upload.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            load(keystorePropertiesFile.inputStream())
        }
    }

fun getKeystoreProperty(key: String, envVar: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(envVar)

android {
    namespace = "com.monday8am.edgelab.explorer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.monday8am.edgelab.explorer"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // HuggingFace OAuth client ID from gradle.properties or environment variable
        val hfClientId =
            project.findProperty("HF_CLIENT_ID")?.toString() ?: System.getenv("HF_CLIENT_ID") ?: ""
        buildConfigField("String", "HF_CLIENT_ID", "\"$hfClientId\"")

        // AppAuth redirect scheme placeholder
        manifestPlaceholders["appAuthRedirectScheme"] = "edgelab"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = getKeystoreProperty("storeFile", "KEYSTORE_FILE")
            if (storeFilePath != null) {
                // Resolve relative to project root (works for both local and CI)
                storeFile = rootProject.file(storeFilePath)
                storePassword = getKeystoreProperty("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = getKeystoreProperty("keyAlias", "KEY_ALIAS")
                keyPassword = getKeystoreProperty("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

    packaging { resources { excludes += "META-INF/*" } }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = false
        // Suppress warnings for prototype
        disable +=
            setOf(
                "ComposeModifierMissing", // Too strict for prototype
                "IconDipSize", // Icon density mismatch in xhdpi (not critical for prototype)
                "AndroidGradlePluginVersion", // Using AGP 9.0 rc2 intentionally
                "GradleDependency", // Dependency version checks
                "NewerVersionAvailable", // Version update suggestions
                "MissingClass", // AppAuth class detection issues during refactoring
            )
    }
}

dependencies {
    // Core module brings all infrastructure (inference, download, OAuth, storage)
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // DataStore (needed for Dependencies.kt)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)

    // Firebase (commented out for now)
    // implementation(platform(libs.firebase.bom))
    // implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    lintChecks(libs.compose.lint.checks)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
