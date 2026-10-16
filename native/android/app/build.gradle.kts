plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.casco.kettle.shell"
  compileSdk = 36
  // React Native's NDK (react-native/gradle/libs.versions.toml). Without one AGP can't strip the
  // AAR's native libraries and packages them with symbols, which would skew the Q8 size comparison.
  ndkVersion = "27.1.12297006"

  defaultConfig {
    // A separate id so the spike installs beside the real app instead of over it.
    applicationId = "com.casco.kettle.spike"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "spike"
    // Matches reactNativeArchitectures in the prebuilt project, so the Q8 size comparison is
    // like for like (react-android and Hermes ship every ABI otherwise).
    ndk { abiFilters += "arm64-v8a" }
  }

  buildTypes {
    // Debug-signed and unminified, the same as the prebuilt app's release build type.
    release { signingConfig = signingConfigs.getByName("debug") }
  }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions { jvmTarget = "17" }

  packaging {
    // RN, Hermes and several Expo modules each ship their own copy of libc++_shared.
    // libworklets: expo-modules-core's published AAR carries an unstripped copy of
    // react-native-worklets' library (same build id, identical once stripped), which the in-repo
    // build excludes and publishing doesn't.
    jniLibs { pickFirsts += listOf("**/libc++_shared.so", "**/libworklets.so") }
  }
}

dependencies {
  implementation("com.casco.kettle:brownfield:1.0.0")
  // The AAR's POM gives every Expo module runtime scope only, so BrownfieldMessaging isn't on the
  // host's compile classpath without this. Has to track the expo-brownfield version in package.json.
  implementation("expo.modules.brownfield:expo.modules.brownfield:57.0.24")

  // BrownfieldActivity is an AppCompatActivity, and the AAR only compiles against fragment-ktx.
  implementation("androidx.appcompat:appcompat:1.7.0")
  implementation("androidx.fragment:fragment-ktx:1.8.6")

  implementation(platform("androidx.compose:compose-bom:2025.05.01"))
  implementation("androidx.compose.material3:material3")
  implementation("androidx.activity:activity-compose:1.10.1")
}
