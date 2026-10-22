import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.casco.kettle.benchmark"
    compileSdk = 36

    defaultConfig {
        // Macrobenchmark's floor for startup timing with a full-display marker.
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Only to match `:app`'s, which is the shell this installs alongside its own APK.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    targetProjectPath = ":app"
    // Self-instrumenting: the test APK drives other packages through the shell, so it can launch
    // today's app (`.bench`, built outside this project) as well as the shell it was built with.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

androidComponents {
    // Only the benchmark variant means anything: a debuggable debug target would time the debugger.
    beforeVariants { variant -> variant.enable = variant.buildType == "benchmark" }
}

dependencies {
    implementation(libs.benchmark.macro.junit4)
    implementation(libs.uiautomator)
    implementation(libs.androidx.test.junit)
}
