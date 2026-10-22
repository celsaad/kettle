import com.android.build.api.variant.HostTestBuilder
import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** The RN project root: the shell reads its versions from there rather than repeating them. */
val rnRoot: Directory = rootProject.layout.projectDirectory.dir("../..")

/** A JSON object as JsonSlurper reads one. Unchecked: a file of the wrong shape fails the build here. */
@Suppress("UNCHECKED_CAST")
fun Any?.asJsonObject(): Map<String, Any?> = this as Map<String, Any?>

fun readJson(path: String): Map<String, Any?> = JsonSlurper().parse(rnRoot.file(path).asFile).asJsonObject()

/** An Expo module's installed version, which is the version its published Android artifact carries. */
fun npmVersion(pkg: String): String = readJson("node_modules/$pkg/package.json")["version"] as String

val rnNdkVersion: String =
    Regex("""^ndkVersion\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
        .find(rnRoot.file("node_modules/react-native/gradle/libs.versions.toml").asFile.readText())
        ?.groupValues
        ?.get(1)
        ?: error("ndkVersion not found in react-native's libs.versions.toml")

val expo = readJson("app.json")["expo"].asJsonObject()
val expoAndroid = expo["android"].asJsonObject()

/** A config plugin's options in app.json, which prebuild writes into android/app and the AAR never carries. */
fun pluginOption(
    plugin: String,
    option: String,
): String {
    val entry = (expo["plugins"] as List<*>).filterIsInstance<List<*>>().single { it.first() == plugin }
    return entry[1].asJsonObject()[option] as String
}

/** One of the upload key's settings, as android.yml passes them in. Read, never written to a file. */
fun uploadKeySetting(setting: String): String? = providers.environmentVariable("KETTLE_UPLOAD_$setting").orNull

android {
    namespace = "com.casco.kettle.shell"
    compileSdk = 36
    // React Native's NDK, read from its own version catalog, which CI's NDK install reads too. Without
    // it AGP can't strip the RN AAR's native libraries, and packages them with their symbols.
    ndkVersion = rnNdkVersion

    defaultConfig {
        applicationId = expoAndroid["package"] as String
        minSdk = 24
        targetSdk = 36
        // app.json's, which /bump moves: the shell replaces the Play app in place, so it continues
        // that app's versionCode line rather than starting one of its own.
        versionCode = expoAndroid["versionCode"] as Int
        versionName = expo["version"] as String
        // app.json's `name`, lowercase as the launcher has always shown it. A placeholder rather than a
        // string resource, so `R.string` stays exactly the strings generated from the bundles.
        manifestPlaceholders["appName"] = expo["name"] as String
        resValue("color", "splashscreen_background", pluginOption("expo-splash-screen", "backgroundColor"))
        resValue("color", "notification_icon_color", pluginOption("expo-notifications", "color"))
        // expo-system-ui sets the night mode from this on every launch, and its library default is
        // `light`: without it, Settings → Appearance → System stays light on a dark phone. Prebuild
        // wrote it from app.json, preferring the android-specific key, and left the default when unset.
        // It goes with the AAR: once expo-system-ui is gone, nothing reads it.
        (expoAndroid["userInterfaceStyle"] ?: expo["userInterfaceStyle"])?.let {
            resValue("string", "expo_system_ui_user_interface_style", it as String)
        }
        // The ABIs the AAR was built for, under the property the RN build takes them as. React Native's
        // and Hermes's own artifacts carry every ABI whatever the AAR has, so without the filter an arm64
        // build packages a partial x86 app that installs on an emulator and crashes there.
        providers.gradleProperty("reactNativeArchitectures").orNull?.let { ndk { abiFilters += it.split(",") } }
    }

    signingConfigs {
        // Expo's template debug key, the one every prebuilt debug build is signed with. Sharing it lets
        // the shell install over a dev build of today's app, which is an update-in-place check for free.
        // It's public, like every RN project's debug key: it signs nothing that reaches Play.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The Play *upload* key, which android.yml restores from its secrets for the length of the job
        // (docs/building-android.md, and the signing entry in docs/decisions.md). Nowhere else has it, so
        // a release built anywhere else is unsigned rather than signed with something Play would refuse;
        // android.yml checks the certificate on what it built against the pinned upload fingerprint.
        uploadKeySetting("KEYSTORE")?.let { keystore ->
            create("upload") {
                storeFile = file(keystore)
                storePassword = uploadKeySetting("KEYSTORE_PASSWORD")
                keyAlias = uploadKeySetting("KEY_ALIAS")
                keyPassword = uploadKeySetting("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Side by side with the Play install on a phone, never over it.
        debug {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appName"] = "Kettle Dev"
        }
        release {
            signingConfig = signingConfigs.findByName("upload")
        }
        // What `:benchmark` launches: a release build, side by side like debug, signed with the debug
        // key so it builds anywhere, and profileable (src/benchmark). `.bench2` because `.bench` is
        // today's app built the same way, which it is timed against (scripts/startup-benchmark.ps1).
        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".bench2"
            manifestPlaceholders["appName"] = "Kettle Bench Shell"
            signingConfig = signingConfigs.getByName("debug")
            // The RN AAR and the libraries publish debug and release only.
            matchingFallbacks += "release"
        }
    }

    packaging {
        // RN, Hermes and several Expo modules each ship their own copy of libc++_shared. libworklets:
        // expo-modules-core's published AAR carries an unstripped copy of react-native-worklets'
        // library, the same build id (an M0 build fact).
        jniLibs { pickFirsts += listOf("**/libc++_shared.so", "**/libworklets.so") }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Robolectric reads the merged resources, generated strings included.
    testOptions { unitTests.isIncludeAndroidResources = true }

    // The zero-warning rule ktlint and detekt hold. Lint's own default fails on errors only.
    lint { warningsAsErrors = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

/** The RN locale bundles, read in place so there is only one copy of every translation. */
val localesDir: Directory = rootProject.layout.projectDirectory.dir("../../src/i18n/locales")

/**
 * The `:strings` generator, run on every build that needs resources. Its output is a build directory
 * and never committed: a generated file in the tree can go stale, and a check that it hasn't is one
 * more thing to fail, where a file the build always writes can't drift at all. It is up to date
 * until a bundle or the generator changes.
 */
abstract class GenerateStrings : JavaExec() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val locales: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty
}

val stringsGenerator: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
}

val generateStrings by tasks.registering(GenerateStrings::class) {
    classpath = stringsGenerator
    mainClass.set("com.casco.kettle.strings.MainKt")
    locales.set(localesDir)
    argumentProviders.add { listOf(locales.get().asFile.path, output.get().asFile.path) }
}

/**
 * The `:theme` generator, the same way: `theme.json` becomes a Kotlin source file in a build directory,
 * so a color changed there reaches Compose on the next build with no hand edit.
 */
abstract class GenerateTheme : JavaExec() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val theme: RegularFileProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty
}

val themeGenerator: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
}

/** The theme RN reads too, read in place, so a color has one home for both UIs. */
val themeFile: RegularFile = rootProject.layout.projectDirectory.file("../../src/constants/theme.json")

val generateTheme by tasks.registering(GenerateTheme::class) {
    classpath = themeGenerator
    mainClass.set("com.casco.kettle.theme.MainKt")
    theme.set(themeFile)
    argumentProviders.add { listOf(theme.get().asFile.path, output.get().asFile.path) }
}

androidComponents {
    // The unit tests run on debug alone: release and benchmark would run the same tests on the same
    // resources, and without ui-test-manifest's activity, which only a debug dependency can merge in.
    listOf("release", "benchmark").forEach { buildType ->
        beforeVariants(selector().withBuildType(buildType)) { variant ->
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
        }
    }
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateStrings, GenerateStrings::output)
        // `java`, not `kotlin`: Kotlin 2.1's Android plugin compiles .kt files in the Java sources but
        // doesn't read AGP's `kotlin` set, which left the generated file out of the build.
        variant.sources.java?.addGeneratedSourceDirectory(generateTheme, GenerateTheme::output)
    }
}

/** The same bundles, for the round-trip test to hold every generated string against its source. */
class LocaleBundles(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val dir: Directory,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Dkettle.localesDir=${dir.asFile.absolutePath}")
}

/** The same theme, for the theme test to hold every generated token against its source. */
class ThemeSource(
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val file: RegularFile,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Dkettle.themeFile=${file.asFile.absolutePath}")
}

tasks.withType<Test>().configureEach {
    jvmArgumentProviders.add(LocaleBundles(localesDir))
    jvmArgumentProviders.add(ThemeSource(themeFile))
    // Robolectric refuses to run SDK 35 or later on a JVM older than 21, and these tests run at the
    // app's targetSdk. Only the test JVM moves; everything still compiles for 17. No toolchain
    // resolver is configured, so a machine without a JDK 21 fails here loudly rather than downloading one.
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

dependencies {
    // The RN app, published by scripts/native-dev.ps1 (or CI) from the prebuilt project.
    implementation("com.casco.kettle:brownfield:1.0.0")
    // The AAR's POM gives every Expo module runtime scope only, so the ones the shell calls directly
    // are declared again here, at the versions node_modules installed.
    implementation("expo.modules.brownfield:expo.modules.brownfield:${npmVersion("expo-brownfield")}")
    implementation("host.exp.exponent:expo.core:${npmVersion("expo")}")
    implementation("host.exp.exponent:expo.modules:${npmVersion("expo-modules-core")}")
    implementation("host.exp.exponent:expo.modules.splashscreen:${npmVersion("expo-splash-screen")}")
    // BrownfieldActivity is an AppCompatActivity, and the AAR only compiles against fragment-ktx.
    implementation(libs.appcompat)
    implementation(libs.fragment.ktx)

    implementation(project(":shared"))
    stringsGenerator(project(":strings"))
    themeGenerator(project(":theme"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(project(":strings"))
    testImplementation(project(":theme"))
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // The empty activity the compose rule launches. A debug dependency, never a release one.
    debugImplementation(libs.compose.ui.test.manifest)
}
