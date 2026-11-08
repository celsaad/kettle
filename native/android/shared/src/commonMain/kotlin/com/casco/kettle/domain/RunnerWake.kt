package com.casco.kettle.domain

import kotlin.math.ceil

// When the service next has to wake (#305): the moments RunnerTiming.kt's readings change, so the
// service sleeps between them rather than polling. Kept beside those rules, which it mirrors, and held
// to them by RunnerTimingTest's NextWake cases, which run a sleeper against a 1 Hz ticker.

/**
 * When the service should next send a [RunnerCommand.TimeUpdate] after [now]: the first whole second
 * of the step's clock at which [reading] says something new, a cue or the end. Null when nothing will,
 * a paused step or one only the user ends. The service sleeps until then rather than polling at 1 Hz
 * (#305), and since the moment is on the step's own second boundaries, floor semantics hold: a wake
 * lands on the second the ticker's would.
 */
fun nextTimeUpdateAt(
    snapshot: RunnerSnapshot,
    now: Long,
): Long? {
    val step = snapshot.step
    val clock = snapshot.clock
    if (step == null || clock.paused) return null
    val elapsed = elapsedSec(clock, now)
    // A step already past its end wakes at once. A wake that never ran (seen once on a device, #306)
    // otherwise left nothing scheduled, since every moment below is in its past, and the session sat
    // on 0:00 for good, through a Resume too. Waking now lets the next update end it.
    val overdue = endSecond(step, clock.stepEndSec)?.let { elapsed >= it } == true
    val second = wakeSeconds(step, clock.stepEndSec).filter { it > elapsed }.minOrNull()
    return if (overdue) now else second?.let { clock.phaseStartedAt + clock.pausedMs + it * MS_PER_SECOND }
}

/** The elapsed second at which [step] ends by itself, or null for one only the user ends. */
private fun endSecond(
    step: RunnerStep,
    endSec: Double,
): Long? =
    when (step) {
        is RunnerStep.Hold -> step.holdEndSec?.let(::wholeSecondAt)
        is RunnerStep.Rest -> wholeSecondAt(endSec)
        is RunnerStep.Interval -> if (step.countUp) null else wholeSecondAt(endSec)
        is RunnerStep.Reps -> null
    }

/** Every elapsed second at which [reading] can change for [step]: its milestone, last ticks and end. */
private fun wakeSeconds(
    step: RunnerStep,
    endSec: Double,
): List<Long> =
    when (step) {
        is RunnerStep.Hold -> {
            val end = step.holdEndSec
            val target = step.holdTargetSec?.takeIf { end == null || it < end }
            listOfNotNull(target?.let(::wholeSecondAt)) + end?.let(::lastSeconds).orEmpty()
        }

        is RunnerStep.Rest -> {
            lastSeconds(endSec)
        }

        is RunnerStep.Interval -> {
            val halfway = if (step.variant == IntervalVariant.HIIT) step.targetSec / 2 else null
            if (step.countUp) {
                emptyList()
            } else {
                listOfNotNull(halfway?.let { wholeSecondAt(endSec - it) }) +
                    lastSeconds(endSec)
            }
        }

        is RunnerStep.Reps -> {
            emptyList()
        }
    }

/** The ticks of the last [TICK_SECONDS] and the end itself. */
private fun lastSeconds(endSec: Double): List<Long> =
    (wholeSecondAt(endSec - TICK_SECONDS)..wholeSecondAt(endSec)).toList()

/** The first whole elapsed second at or past [sec]. */
private fun wholeSecondAt(sec: Double): Long = ceil(sec).toLong()
