// PROTOTYPE (#401), never merged: does Robolectric's qualifier pick Portuguese from CMP's Res, and
// does a plural still need the explicit zero branch?
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.compose")
}

kotlin {
    android {
        namespace = "com.casco.kettle.resspike"
        compileSdk = 37
        minSdk = 26
        androidResources { enable = true }
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.12.2")
            implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
            implementation("org.jetbrains.compose.components:components-resources:1.12.2")
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit4)
            implementation(libs.robolectric)
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.espresso.core) // :app pins 3.7.0 for SDK 37 the same way
        }
    }
}

compose.resources {
    packageOfResClass = "spike.res"
    publicResClass = true
}

// :app's two Robolectric settings (its build file says why).
tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
}
