// M0 spike host (docs/native-migration-plan.md). Throwaway: it exists to answer the spike's
// questions on a device, not as the shape M2 will ship.
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
    // The brownfield AAR and the Expo modules it depends on are published here by
    // `./gradlew publishBrownfieldDebugPublicationToMavenLocal` in the prebuilt android/.
    mavenLocal()
    google()
    mavenCentral()
    maven { url = uri("https://www.jitpack.io") }
  }
}

rootProject.name = "kettle-shell"
include(":app")
