// The native app (docs/native-migration-plan.md). `:shared` is the Kotlin Multiplatform domain the
// iOS app reuses later; `:app` is the Compose shell that will host it.
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
include(":shared", ":app")
