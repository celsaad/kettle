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

    sourceSets {
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation(libs.junit.jupiter)
            runtimeOnly(libs.junit.launcher)
            // Reads the corpus files, and only in the test. Which YAML library `:shared` itself
            // parses users' files with is #144's decision, not this runner's.
            implementation(libs.snakeyaml.engine)
        }
    }
}

// The corpus is read in place from the repo's `conformance/`, never copied, so there is only one.
// Declaring it as an input is what makes an edited golden rerun the tests rather than leave the task
// UP-TO-DATE and green.
val conformanceDir = rootProject.layout.projectDirectory.dir("../../conformance")

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    inputs.dir(conformanceDir).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("conformance")
    systemProperty("kettle.conformanceDir", conformanceDir.asFile.absolutePath)
    testLogging {
        events("passed", "skipped", "failed")
    }
}
