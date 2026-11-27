import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The guards on the repo's own prose and published files (#352): the site's samples and example
// libraries, the docs' samples, the format mirrors' type tables, and the Play copy. Tests only, read
// against the files in place, with `:shared` doing the parsing and merging the app does. Nothing here
// ships, and nothing depends on it.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(project(":shared"))
    // library-schema.json, read for the format mirrors' expected fields.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

/**
 * The repo root, handed to the tests through a provider for the reason `:shared`'s corpus is: a plain
 * `systemProperty` would put this checkout's absolute path in the cache key. Every directory the
 * checks read is an input, so an edited doc or page reruns them rather than leaving the task
 * UP-TO-DATE and green.
 */
class RepoFiles(
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val files: ConfigurableFileCollection,
    @get:Internal
    val root: Directory,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Dkettle.repoRoot=${root.asFile.absolutePath}")
}

val repoRoot: Directory by rootProject.extra

tasks.test {
    useJUnitPlatform()
    val root = repoRoot
    val read =
        files(
            root.dir("docs"),
            root.dir("site"),
            root.dir("store"),
            root.file("CHANGELOG.md"),
            root.dir("content"),
        )
    jvmArgumentProviders.add(RepoFiles(objects.fileCollection().from(read), root))
    testLogging {
        events("failed")
    }
}
