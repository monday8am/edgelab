plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.ktfmt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

subprojects {
    pluginManager.apply("com.ncorti.ktfmt.gradle")
    extensions.configure<com.ncorti.ktfmt.gradle.KtfmtExtension> { kotlinLangStyle() }
}
