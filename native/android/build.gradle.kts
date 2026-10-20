import io.gitlab.arturbosch.detekt.Detekt

plugins {
    // Gives the root its own `check`, so ktlint covers this script and settings.gradle.kts too.
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

// The catalog accessor is the root project's; inside `allprojects` it would resolve per subproject.
val ktlintVersion = libs.versions.ktlint.get()
val detektKotlinVersion = libs.versions.detektKotlin.get()

// Zero warnings and zero errors, the same rule `pnpm run lint` holds on the TS side. Both tools fail
// `check` on any finding: ktlint by default, detekt because its default `maxIssues` is 0 and
// `warningsAsErrors` turns its warning-severity rules into findings too.
allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    ktlint {
        version.set(ktlintVersion)
    }

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("detekt.yml"))
        // Every source set, KMP's included: detekt's own default is `src/main` and `src/test` only,
        // which would leave `commonMain` and `jvmTest` unlinted and the job green.
        source.setFrom(files("src"))
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
    }

    // Without the pin Gradle resolves detekt's compiler up to the build's Kotlin, and detekt aborts.
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlinVersion)
        }
    }
}
