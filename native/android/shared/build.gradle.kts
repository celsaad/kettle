import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // The JVM is what `:app` and the corpus runner need, and an Android app consumes a JVM target as
    // it is. `androidTarget()` waits for the first code that needs an Android API.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // M8's groundwork (#368): devices and the Apple-silicon simulator. No `iosX64`, since no Intel Mac
    // builds or tests it. Only a Mac can compile these; on Linux KGP skips them, so CI's Linux job
    // never needs one.
    iosArm64()
    iosSimulatorArm64()

    // The compiler's warnings held to the same zero as ktlint's and detekt's.
    compilerOptions { allWarningsAsErrors.set(true) }

    sourceSets {
        commonMain.dependencies {
            // The YAML loader for users' files; why this one is in `YamlLoad.kt`.
            implementation(libs.snakeyaml.engine.kmp)
            // Calendar days in the reader's zone, which the Stats selectors' windows are counted in.
            // `api`, since those selectors take an Instant and a TimeZone.
            api(libs.kotlinx.datetime)
            // The runner's state file. Its element API only, as `:strings` and `:theme` use it, so no
            // compiler plugin: the snapshot's JSON is written out by hand and versioned there.
            implementation(libs.kotlinx.serialization.json)
        }
        // The corpus runner, which runs on the JVM and the simulator, so `kotlin.test` rather than
        // Jupiter (#368). On the JVM it runs on the Jupiter platform below, at KGP's own version.
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation(libs.junit.jupiter)
            implementation(libs.json.skema)
            runtimeOnly(libs.junit.launcher)
        }
    }
}

/**
 * The corpus is read in place from the repo's `conformance/`, never copied, so there is only one.
 * It is a task input, so an edited golden reruns the tests rather than leaving the task UP-TO-DATE
 * and green. It reaches the JVM through a provider because a plain `systemProperty` would put this
 * checkout's absolute path in the cache key, and no other machine or checkout would ever hit it.
 */
class ConformanceCorpus(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val dir: Directory,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Dkettle.conformanceDir=${dir.asFile.absolutePath}")
}

val repoRoot: Directory by rootProject.extra

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    jvmArgumentProviders.add(ConformanceCorpus(repoRoot.dir("conformance")))
    // `source:` cases read these in place too, so an edit to the shipped content reruns the tests.
    listOf("content", "site/examples").forEach {
        inputs.dir(repoRoot.dir(it)).withPathSensitivity(PathSensitivity.RELATIVE)
    }
    // `-PupdateConformance` rewrites every suite's golden from Kotlin, the corpus's only writer since
    // #348; never up to date, so it always runs.
    if (providers.gradleProperty("updateConformance").isPresent) {
        systemProperty("kettle.updateConformance", "true")
        outputs.upToDateWhen { false }
    }
    // In full, so a corpus failure names its case in the CI log: one test now holds every case (#368).
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

// The same corpus on the simulator, which sees the Mac's file system, so it is read in place there
// too. `simctl spawn` passes the test binary each `SIMCTL_CHILD_` variable with the prefix dropped,
// left untracked so the checkout's absolute path stays out of any key. The Kotlin plugin never caches
// or skips this task, so it reruns on every build whatever its inputs say. It only ever checks the
// goldens: `-PupdateConformance` is the JVM's.
tasks.named<KotlinNativeSimulatorTest>("iosSimulatorArm64Test") {
    environment(
        "SIMCTL_CHILD_KETTLE_CONFORMANCE_DIR",
        repoRoot.dir("conformance").asFile.absolutePath,
        tracked = false,
    )
    listOf("conformance", "content", "site/examples").forEach {
        inputs.dir(repoRoot.dir(it)).withPathSensitivity(PathSensitivity.RELATIVE)
    }
    // In full, so a corpus failure names its case in the CI log: one test now holds every case (#368).
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
