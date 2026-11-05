package com.casco.kettle.domain
import kotlin.math.floor
import kotlin.math.max

/**
 * The step on screen's clock, in wall-clock epoch milliseconds so a rebuild survives a kill or a
 * reboot (see [ClockAnchor] for where those come from). Pause belongs to it, so it goes with the step
 * that was paused.
 *
 * [stepEndSec] is where the step ends by itself: the rest's seconds, a countdown interval's target
 * (both moved by +30s), a hold's end, and 0 for every step that only ends when the user says so.
 */
data class StepClock(
    val phaseStartedAt: Long,
    val pausedAt: Long?,
    val pausedMs: Long,
    val stepEndSec: Double,
) {
    val paused: Boolean get() = pausedAt != null
}

/**
 * How the service's monotonic clock becomes the wall-clock time the engine is given.
 *
 * Live timing runs on the monotonic clock (`SystemClock.elapsedRealtime()`), which a user setting the
 * time can't move. The snapshot stores wall-clock times instead, because the monotonic clock restarts
 * at a reboot and a rebuild has to work after one. So the service takes one anchor when it starts, a
 * wall-clock reading and a monotonic one at the same moment, and passes every later reading through
 * [wallAt]. A clock change mid-session then can't move a step's end, and a rebuild takes a fresh
 * anchor, so the time measured across a kill is wall-clock time, the only kind that survives one.
 */
data class ClockAnchor(
    val wallMs: Long,
    val monotonicMs: Long,
) {
    fun wallAt(monotonicMs: Long): Long = wallMs + (monotonicMs - this.monotonicMs)
}

private const val MS_PER_SECOND = 1000.0

/**
 * Whole seconds the step on screen has run at [now], paused time excluded: floored, as RN's is, since
 * both what's shown and what's logged are whole seconds. Never below 0.
 */
fun elapsedSec(
    clock: StepClock,
    now: Long,
): Long {
    val until = clock.pausedAt ?: now
    return max(0.0, floor((until - clock.phaseStartedAt - clock.pausedMs) / MS_PER_SECOND)).toLong()
}

/** What a rest or countdown interval shows: the seconds left, never below 0. */
fun remainingSec(
    clock: StepClock,
    now: Long,
): Double = max(0.0, clock.stepEndSec - elapsedSec(clock, now))

internal fun togglePause(
    state: RunnerState,
    now: Long,
): RunnerTransition {
    val snapshot = state.snapshot
    if (snapshot.step == null) return unchanged(state)
    val clock = snapshot.clock
    val toggled =
        when (val pausedAt = clock.pausedAt) {
            null -> clock.copy(pausedAt = now)
            else -> clock.copy(pausedAt = null, pausedMs = clock.pausedMs + (now - pausedAt))
        }
    return RunnerTransition(state.copy(snapshot = snapshot.copy(clock = toggled)), emptyList())
}

/**
 * The rest screen's +30s, by [seconds]: moves the end, and nothing else. Only where the clock's end
 * is the step's end, a rest or a countdown interval, so [StepClock.stepEndSec] never says a hold ends
 * later than it does. RN's moved its end ref on any step, which only its rest screen ever asked for.
 */
internal fun addRestSeconds(
    state: RunnerState,
    seconds: Double,
): RunnerTransition {
    val snapshot = state.snapshot
    val step = snapshot.step
    val countsDown = step is RunnerStep.Rest || (step is RunnerStep.Interval && !step.countUp)
    if (!countsDown) return unchanged(state)
    val clock = snapshot.clock.copy(stepEndSec = max(0.0, snapshot.clock.stepEndSec + seconds))
    return RunnerTransition(state.copy(snapshot = snapshot.copy(clock = clock)), emptyList())
}
