import com.android.build.api.variant.HostTestBuilder
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.casco.kettle.shell"
    compileSdk = 36
    // React Native's NDK (react-native/gradle/libs.versions.toml). Without it AGP can't strip the RN
    // AAR's native libraries once M2 hosts it, and packages them with their symbols.
    ndkVersion = "27.1.12297006"

    defaultConfig {
        // Not com.casco.kettle until M2 ships the shell (#148), so nothing built here can install
        // over the Play app.
        applicationId = "com.casco.kettle.shell"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Robolectric reads the merged resources, generated strings included.
    testOptions { unitTests.isIncludeAndroidResources = true }
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
