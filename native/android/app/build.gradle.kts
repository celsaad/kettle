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

@Suppress("UNCHECKED_CAST")
fun readJson(path: String): Map<String, Any?> = JsonSlurper().parse(rnRoot.file(path).asFile) as Map<String, Any?>

/** An Expo module's installed version, which is the version its published Android artifact carries. */
fun npmVersion(pkg: String): String = readJson("node_modules/$pkg/package.json")["version"] as String

@Suppress("UNCHECKED_CAST")
val expo = readJson("app.json")["expo"] as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
val expoAndroid = expo["android"] as Map<String, Any?>

/** A config plugin's options in app.json, which prebuild writes into android/app and the AAR never carries. */
@Suppress("UNCHECKED_CAST")
fun pluginOption(
    plugin: String,
    option: String,
): String {
    val entry = (expo["plugins"] as List<Any?>).filterIsInstance<List<Any?>>().single { it.first() == plugin }
    return (entry[1] as Map<String, Any?>)[option] as String
}

android {
    namespace = "com.casco.kettle.shell"
    compileSdk = 36
    // React Native's NDK (react-native/gradle/libs.versions.toml). Without it AGP can't strip the RN
    // AAR's native libraries, and packages them with their symbols.
    ndkVersion = "27.1.12297006"

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
    }

    buildTypes {
        // Side by side with the Play install on a phone, never over it.
        debug {
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appName"] = "Kettle Dev"
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
    // The unit tests run on debug alone: release would run the same tests on the same resources, and
    // without ui-test-manifest's activity, which only a debug dependency can merge into the app.
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = false
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
