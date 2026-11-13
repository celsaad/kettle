// The native app (docs/native-migration-plan.md). `:shared` is the Kotlin Multiplatform domain the
// iOS app reuses later; `:app` is the Android app; `:strings` generates `:app`'s string resources from
// the locale bundles, `:theme` its Compose theme from `theme.json`, and `:art` its exercise drawings
// from `src/content/art/`.
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
    }
}

rootProject.name = "kettle"
include(":shared", ":app", ":strings", ":theme", ":art", ":benchmark")
// PROTOTYPE (#401), never merged.
include(":resspike")
