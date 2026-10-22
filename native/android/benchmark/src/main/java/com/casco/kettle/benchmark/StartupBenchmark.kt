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
 * Cold start to a drawn home screen, the shell against today's app (#150, M2 in
 * docs/native-migration-plan.md). The spike's adb numbers stop at the first frame, which RN draws before
 * its JS has rendered anything; this stops at Home's `reportFullyDrawn` (src/hooks/startup-marker.ts),
 * which both builds call from the same JS. Read `timeToFullDisplayMs`, not `timeToInitialDisplayMs`.
 *
 * Both targets are side-by-side release builds on the seed library. Never the Play install: it holds
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
            // the first frame. Home's testID is its resource id; a timeout means no marker, so fail.
            check(device.wait(Until.hasObject(By.res(HOME_SCREEN)), HOME_TIMEOUT_MS)) {
                "$target never showed its home screen"
            }
        }

    companion object {
        /** Today's app, built by rn-app.init.gradle, and the shell's `benchmark` build type. */
        private val TARGETS = listOf("com.casco.kettle.bench", "com.casco.kettle.bench2")

        private const val ITERATIONS = 15
        private const val HOME_SCREEN = "home-screen"
        private const val HOME_TIMEOUT_MS = 10_000L

        /**
         * Two bounds, because neither build has a baseline profile and Play's cloud profile can't be had
         * side by side: None is a fresh install running on the JIT, Full is everything compiled ahead.
         * The JS is Hermes bytecode either way, which neither mode touches.
         */
        @JvmStatic
        @Parameterized.Parameters(name = "{0} {1}")
        fun parameters(): List<Array<Any>> =
            TARGETS.flatMap { target ->
                listOf(CompilationMode.None(), CompilationMode.Full()).map { arrayOf(target, it) }
            }
    }
}
