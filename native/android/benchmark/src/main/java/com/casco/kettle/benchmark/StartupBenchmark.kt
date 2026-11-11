package com.casco.kettle.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Cold start to a drawn home screen (#150, M2 in docs/native-migration-plan.md). The spike's adb numbers
 * stop at the first frame; this stops at native Home's `reportFullyDrawn` (`ReportDrawnWhen` in
 * `HomeScreen.kt`, #235), once the library has loaded and laid out. Read `timeToFullDisplayMs`, not
 * `timeToInitialDisplayMs`. Today's app was a second target until #235 stubbed RN Home, and returns in
 * M7 built from a pinned commit (startup-benchmark.ps1 says why).
 *
 * The target is a release build on the seed library. Never the Play install: it holds
 * the user's real data, and this force-stops its target and resets its compiled code every run.
 */
@RunWith(Parameterized::class)
class StartupBenchmark(
    private val target: String,
    private val compilation: CompilationMode,
) {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartToHome() =
        rule.measureRepeated(
            packageName = target,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilation,
            startupMode = StartupMode.COLD,
            iterations = ITERATIONS,
        ) {
            startActivityAndWait()
            // The metric needs the marker inside the measured block, and startActivityAndWait returns at
            // the first frame. Home's test tag is its resource id, and a timeout means no Home at all.
            check(device.wait(Until.hasObject(By.res(HOME_SCREEN)), HOME_TIMEOUT_MS)) {
                "$target never showed its home screen"
            }
            // The marker lands a frame after the layout that put Home on screen, so the node can be found
            // first. Idle covers the frame; an iteration that still misses it has no full-display
            // time, which startup-benchmark.ps1 refuses rather than averaging over.
            device.waitForIdle()
        }

    companion object {
        /** The shell's `benchmark` build type. */
        private val TARGETS = listOf("com.casco.kettle.bench2")

        private const val ITERATIONS = 15

        /** `HOME_SCREEN_TAG` in HomeScreen.kt. The two share no code, so change both. */
        private const val HOME_SCREEN = "home-screen"
        private const val HOME_TIMEOUT_MS = 10_000L

        /**
         * Two bounds, because neither build has a baseline profile and Play's cloud profile can't be had
         * side by side: None is a fresh install running on the JIT, Full is everything compiled ahead.
         * (The RN baseline's JS is Hermes bytecode either way, which neither mode touches.)
         */
        @JvmStatic
        @Parameterized.Parameters(name = "{0} {1}")
        fun parameters(): List<Array<Any>> =
            TARGETS.flatMap { target ->
                listOf(CompilationMode.None(), CompilationMode.Full()).map { arrayOf(target, it) }
            }
    }
}
