package com.casco.kettle.shell.runner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.casco.kettle.domain.RunnerStep
import com.casco.kettle.domain.StepClock
import com.casco.kettle.domain.TICK_SECONDS
import com.casco.kettle.domain.remainingSec
import com.casco.kettle.shell.theme.RunnerColors
import com.casco.kettle.shell.theme.RunnerPalette

/**
 * The runner's background, which says what the step's clock is doing rather than what type of exercise
 * it is (#418): an AMRAP and a timed hold are both the clock's work, a max-effort hold and a reps set
 * both yours to end.
 */
enum class RunnerField(
    val palette: RunnerPalette,
) {
    /** You set the pace: a reps set, a hold or cardio with no end of its own, anything paused. */
    PACE(RunnerColors.pace),

    /** The count-in, and a rest's last [TICK_SECONDS], with the ticks: black, the number orange. */
    GET_READY(RunnerColors.getReady),

    /** The clock runs the work: HIIT, EMOM, AMRAP, cardio with a duration, a hold that ends itself. */
    WORK(RunnerColors.work),

    REST(RunnerColors.rest),
}

/** The field [entry] is drawn on at [now]. Every screen around a session is [RunnerField.PACE]. */
internal fun runnerField(
    entry: RunnerEntry,
    now: Long,
): RunnerField {
    val view = (entry as? RunnerEntry.Attach)?.view
    return when (view) {
        is RunnerView.CountingIn -> RunnerField.GET_READY
        is RunnerView.Running -> view.snapshot.step?.let { stepField(it, view.snapshot.clock, now) } ?: RunnerField.PACE
        null -> RunnerField.PACE
    }
}

/**
 * Keyed on what ends the step, so it agrees with the engine's own `reading`: a step it ends by itself is
 * work, one only a tap ends is pace. Paused, nothing is running it, so it is pace too.
 */
internal fun stepField(
    step: RunnerStep,
    clock: StepClock,
    now: Long,
): RunnerField {
    val ticking = remainingSec(clock, now) <= TICK_SECONDS
    return when {
        clock.paused -> RunnerField.PACE
        step is RunnerStep.Rest -> if (ticking) RunnerField.GET_READY else RunnerField.REST
        step is RunnerStep.Hold -> if (step.holdEndSec != null) RunnerField.WORK else RunnerField.PACE
        step is RunnerStep.Interval -> if (step.countUp) RunnerField.PACE else RunnerField.WORK
        else -> RunnerField.PACE
    }
}

/**
 * The palette the runner's screen is drawn in. Static: it changes with the field, a few times a minute
 * at most, and everything below reads it, so a dynamic local's per-reader tracking would buy nothing.
 */
internal val LocalRunnerPalette = staticCompositionLocalOf { RunnerColors.pace }

/** The field's colours, which every runner composable reads in place of a fixed [RunnerColors] token. */
internal val runnerColors: RunnerPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalRunnerPalette.current
