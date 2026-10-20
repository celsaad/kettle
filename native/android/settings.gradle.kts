// The native app (docs/native-migration-plan.md). `:shared` is the Kotlin Multiplatform domain the
// iOS app reuses later; `:app` is the Compose shell that will host it; `:strings` generates `:app`'s
// string resources from the RN locale bundles, and `:theme` its Compose theme from `theme.json`.
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
include(":shared", ":app", ":strings", ":theme")
