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
    testLogging {
        events("passed", "skipped", "failed")
    }
}
