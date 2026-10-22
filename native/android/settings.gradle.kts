// The native app (docs/native-migration-plan.md). `:shared` is the Kotlin Multiplatform domain the
// iOS app reuses later; `:app` is the shell, which hosts the RN app from its AAR; `:strings` generates
// `:app`'s string resources from the RN locale bundles, and `:theme` its Compose theme from `theme.json`.
// `:benchmark` times a cold start to a drawn home screen, the shell's against today's app.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // The RN app as an AAR, and the Expo modules it depends on, published here from the prebuilt
        // project (scripts/native-dev.ps1). Last, so a stale local copy can't shadow a real release.
        mavenLocal()
    }
}

rootProject.name = "kettle"
include(":shared", ":app", ":strings", ":theme", ":benchmark")
