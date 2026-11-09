import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    // JVM only for now: it is what `:app` and the corpus runner need, and an Android app consumes a
    // JVM target as it is. `androidTarget()` waits for the first code that needs an Android API, and
    // the iOS targets for M8 and a Mac.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

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
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation(libs.junit.jupiter)
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

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    jvmArgumentProviders.add(ConformanceCorpus(rootProject.layout.projectDirectory.dir("../../conformance")))
    // `source:` cases read these in place too, so an edit to the shipped content reruns the tests.
    listOf("../../src/content", "../../site/examples").forEach {
        inputs.dir(rootProject.layout.projectDirectory.dir(it)).withPathSensitivity(PathSensitivity.RELATIVE)
    }
    // `-PupdateConformance` rewrites the goldens of the suites only Kotlin implements (ConformanceTest's
    // KOTLIN_WRITES), which `pnpm run conformance:update` can't reach; never up to date, so it always runs.
    if (providers.gradleProperty("updateConformance").isPresent) {
        systemProperty("kettle.updateConformance", "true")
        outputs.upToDateWhen { false }
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}
