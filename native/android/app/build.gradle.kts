import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.casco.kettle.shell"
    compileSdk = 36
    // React Native's NDK (react-native/gradle/libs.versions.toml). Without it AGP can't strip the RN
    // AAR's native libraries once M2 hosts it, and packages them with their symbols.
    ndkVersion = "27.1.12297006"

    defaultConfig {
        // Not com.casco.kettle until M2 ships the shell (#148), so nothing built here can install
        // over the Play app.
        applicationId = "com.casco.kettle.shell"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}
