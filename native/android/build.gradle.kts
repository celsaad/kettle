// Same AGP and Kotlin as the React Native side (react-native/gradle/libs.versions.toml), so the
// spike measures brownfield rather than a toolchain mismatch.
plugins {
  id("com.android.application") version "8.12.0" apply false
  id("org.jetbrains.kotlin.android") version "2.1.20" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
}
