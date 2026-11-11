import com.android.build.api.variant.HostTestBuilder
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** One of the upload key's settings, as android.yml passes them in. Read, never written to a file. */
fun uploadKeySetting(setting: String): String? = providers.environmentVariable("KETTLE_UPLOAD_$setting").orNull

android {
    namespace = "com.casco.kettle.shell"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.casco.kettle"
        minSdk = 24
        targetSdk = 36
        // gradle.properties', which /bump moves.
        versionCode = providers.gradleProperty("kettle.versionCode").get().toInt()
        versionName = providers.gradleProperty("kettle.versionName").get()
        // Lowercase, as the launcher has always shown it. A placeholder rather than a string resource,
        // so `R.string` stays the strings generated from the bundles.
        manifestPlaceholders["appName"] = "kettle"
    }

    signingConfigs {
        // The debug key every `.dev` build has been signed with, the RN app's included, so a new `.dev`
        // installs over an old one in place, data and all. Changing it breaks that for every dev
        // install. It's public: it signs nothing that reaches Play.
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
            // The libraries publish debug and release only.
            matchingFallbacks += "release"
        }
    }

    // `NavigateReceiver`, adb's way to steer the navigator: the dev build's, and the benchmark build's, so
    // route-check runs against a release-built shell too (#309). Never Play's release.
    sourceSets {
        getByName("debug").java.srcDir("src/steering/java")
        getByName("benchmark").java.srcDir("src/steering/java")
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // kotlinx-datetime is java.time underneath on Android, which the platform only has from API 26,
        // and minSdk is 24 because the RN app this replaces in place is (Expo SDK 57's floor). Without
        // this, exercise progress and Stats would crash on Android 7. Goes at M7 with React Native,
        // when minSdk can rise to 26 (docs/native-migration-plan.md).
        isCoreLibraryDesugaringEnabled = true
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

/** The session's three sounds, `assets/sounds/` at the repo root. */
val soundsDir: Directory = rootProject.layout.projectDirectory.dir("../../assets/sounds")

/** The locale bundles, read in place so there is only one copy of every translation. */
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
 * so a color changed there reaches Compose on the next build with no hand edit. The faces its `Fonts`
 * names are copied out of `theme/fonts` into `res/font` by the same run, so the generated families
 * can't name a face this build didn't bundle.
 */
abstract class GenerateTheme : JavaExec() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val theme: RegularFileProperty

    /** The whole fonts directory, since which faces are read is theme.json's to say. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val fonts: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:OutputDirectory
    abstract val res: DirectoryProperty
}

val themeGenerator: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
}

/** The shipped seed and content packs (`ShippedContent`), at the asset root. */
val contentDir: Directory = rootProject.layout.projectDirectory.dir("../../src/content")

/** The theme, read in place, so a color has one home. */
val themeFile: RegularFile = rootProject.layout.projectDirectory.file("../../src/constants/theme.json")

/**
 * The faces theme.json names, committed with their OFL licences, since the build reads nothing a JS
 * install provides (see the fonts entry in docs/decisions.md).
 */
val fontsDir: Directory = rootProject.layout.projectDirectory.dir("theme/fonts")

val generateTheme by tasks.registering(GenerateTheme::class) {
    classpath = themeGenerator
    mainClass.set("com.casco.kettle.theme.MainKt")
    theme.set(themeFile)
    fonts.set(fontsDir)
    argumentProviders.add { listOf(theme, fonts, output, res).map { it.get().asFile.path } }
}

/**
 * The `:art` generator, the same way again: each drawing in `src/content/art/` becomes a VectorDrawable,
 * and `art.json` an id → `R.drawable` lookup. It fails the build on any SVG outside the subset it
 * accepts, and on a map and directory that disagree.
 */
abstract class GenerateArt : JavaExec() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val map: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val drawings: DirectoryProperty

    @get:OutputDirectory
    abstract val res: DirectoryProperty

    /** Beside `res` in one task, so the lookup can never name a drawable this build didn't write. */
    @get:OutputDirectory
    abstract val source: DirectoryProperty
}

val artGenerator: Configuration by configurations.creating {
    isCanBeConsumed = false
    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
}

val generateArt by tasks.registering(GenerateArt::class) {
    classpath = artGenerator
    mainClass.set("com.casco.kettle.art.MainKt")
    map.set(contentDir.file("art.json"))
    drawings.set(contentDir.dir("art"))
    argumentProviders.add {
        listOf(map, drawings, res, source).map { it.get().asFile.path }
    }
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
        variant.sources.res?.addGeneratedSourceDirectory(generateArt, GenerateArt::res)
        variant.sources.java?.addGeneratedSourceDirectory(generateArt, GenerateArt::source)
        // `java`, not `kotlin`: Kotlin 2.1's Android plugin compiles .kt files in the Java sources but
        // doesn't read AGP's `kotlin` set, which left the generated file out of the build.
        variant.sources.java?.addGeneratedSourceDirectory(generateTheme, GenerateTheme::output)
        variant.sources.res?.addGeneratedSourceDirectory(generateTheme, GenerateTheme::res)
        // The seed and packs, packaged as they are: nothing to generate, so read in place.
        variant.sources.assets?.addStaticSourceDirectory(contentDir.asFile.path)
        // The runner's cues (#305), the files RN played, read in place for the same reason.
        variant.sources.assets?.addStaticSourceDirectory(soundsDir.asFile.path)
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
    // Two Robolectric JVMs, not one: CI's runner has four cores and the suite is the build's longest
    // task. Four forks measured no faster than two, each paying Robolectric's startup again.
    maxParallelForks = 2
    jvmArgumentProviders.add(LocaleBundles(localesDir))
    jvmArgumentProviders.add(ThemeSource(themeFile))
    // Robolectric refuses to run SDK 35 or later on a JVM older than 21, and these tests run at the
    // app's targetSdk. Only the test JVM moves; everything still compiles for 17. No toolchain
    // resolver is configured, so a machine without a JDK 21 fails here loudly rather than downloading one.
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    // Used directly throughout (NotificationCompat, ViewCompat, FileProvider, coroutines), and declared
    // at what the shell resolved before M7, when RN's modules raised them, so the removal moved
    // neither.
    implementation(libs.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(project(":shared"))
    stringsGenerator(project(":strings"))
    themeGenerator(project(":theme"))
    artGenerator(project(":art"))

    implementation(platform(libs.compose.bom))
    // Over the BOM, at what the app ships (libs.versions.toml's `composeShipped`).
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    // The splash theme and its hold (SplashHold).
    implementation(libs.core.splashscreen)
    implementation(libs.navigation.compose)
    // Support's tip jar (#198). expo-iap went with RN's Support, so this is the APK's only Play Billing.
    implementation(libs.billing)
    // The workout editor's block drag (#242). Nothing it adds touches the network (#226).
    implementation(libs.reorderable)
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
