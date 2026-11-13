package com.casco.kettle.domain

import kotlin.math.max

// When a step ends by itself and what it cues on the way (§2.2–2.4, §2.6 of #294's inventory), and
// what a step starts with. RN did both from a 1 Hz interval and a render-time reset; here the service
// sends a time update whenever it likes, and the engine decides from the clock in the snapshot.

/**
 * Which step this is, as RN's reset keyed it: `index:memberKey:kind:setIndex`. A swap or an ad-hoc
 * add changes it without the index moving; adding or dropping a set doesn't change it. A parked
 * ad-hoc session is `index:none`.
 */
internal fun stepIdentity(snapshot: RunnerSnapshot): String {
    val step = snapshot.step ?: return "${snapshot.stepIndex}:none"
    val kind =
        when (step) {
            is RunnerStep.Hold -> "hold"
            is RunnerStep.Reps -> "reps"
            is RunnerStep.Interval -> "interval"
            is RunnerStep.Rest -> "rest"
        }
    return "${snapshot.stepIndex}:${step.memberKey}:$kind:${(step as? WorkStep)?.setIndex ?: 0}"
}

/** Where [step] ends by itself; 0 for one only the user ends. A hold's end is here too, for the alert. */
private fun stepEndSec(step: RunnerStep?): Double =
    when (step) {
        is RunnerStep.Rest -> step.seconds
        is RunnerStep.Interval -> if (step.countUp) 0.0 else step.targetSec
        is RunnerStep.Hold -> step.holdEndSec ?: 0.0
        is RunnerStep.Reps, null -> 0.0
    }

/**
 * The step on screen started afresh at [now]: a new clock, **not paused**, and its inputs seeded.
 *
 * Pause belongs to the step's clock and clears on every step change. RN kept one session-wide flag
 * and re-seeded the next step paused in its step-change block, so a rest after a paused interval
 * sat at its full time with no Resume on screen. That is deliberately not ported.
 */
internal fun seeded(
    state: RunnerState,
    now: Long,
): RunnerState {
    val snapshot = state.snapshot
    val step = snapshot.step
    return state.copy(
        snapshot =
            snapshot.copy(
                visit = snapshot.visit + 1,
                clock = StepClock(now, null, 0, stepEndSec(step)),
                inputs = seededInputs(state, step),
            ),
        cues = CueMarks(),
    )
}

/**
 * Reps from the target every set, since varying them is what's being logged, and an EMOM minute from
 * its own; load carried from the member's last logged set, else the target, and left alone off a
 * reps step; rounds and extra reps from zero; RPE never re-seeded.
 */
private fun seededInputs(
    state: RunnerState,
    step: RunnerStep?,
): StepInputs {
    val inputs = state.snapshot.inputs
    val reps =
        when (step) {
            is RunnerStep.Reps -> step.targetReps
            is RunnerStep.Interval -> step.targetReps ?: 0
            else -> 0
        }
    val weightKg =
        if (step is RunnerStep.Reps) {
            val carried = (state.memberEntry(step.memberKey) as? SessionEntry.Reps)?.sets?.lastOrNull()?.weightKg
            carried ?: step.targetWeightKg ?: 0.0
        } else {
            inputs.weightKg
        }
    return inputs.copy(reps = reps, weightKg = weightKg, roundsCompleted = 0, extraReps = 0)
}

/** What the clock says about the step on screen at one reading. */
private data class Reading(
    val ended: Boolean,
    val tick: Boolean,
    val milestone: Boolean,
)

private val QUIET = Reading(ended = false, tick = false, milestone = false)

/** A countdown's last seconds, each of which ticks. Public for the app, whose screen marks them too. */
const val TICK_SECONDS = 3

/**
 * A time update: the step on screen ends by itself, or cues, or neither.
 *
 * - Countdowns (rest, and intervals with a target) end at `remaining <= 0`, holds at the *top* of
 *   their range. A max-effort hold and count-up cardio never end by themselves.
 * - **After a gap it advances at most one step**, and the next starts fresh at [now], the moment of
 *   return. The time away isn't carried into later steps; that is deliberate now.
 * - It does nothing when paused, or when aimed at a step already left ([visit] isn't the snapshot's).
 *
 * Ticks sound once per whole second, so a service ticking faster than 1 Hz doesn't play more of them,
 * and a step ending in this update cues only its exercise change.
 */
internal fun timeUpdate(
    state: RunnerState,
    visit: Long,
    now: Long,
): RunnerTransition {
    val snapshot = state.snapshot
    val step = snapshot.step
    if (step == null || visit != snapshot.visit || snapshot.clock.paused) return unchanged(state)
    val elapsed = elapsedSec(snapshot.clock, now)
    val reading = reading(step, snapshot.clock.stepEndSec, elapsed)
    return if (reading.ended) {
        val moved = advance(state, now)
        val ended = RunnerEffect.StepEnded(step, snapshot.steps.getOrNull(snapshot.stepIndex + 1))
        RunnerTransition(moved.state, listOf(ended) + moved.effects)
    } else {
        cued(state, reading, elapsed)
    }
}

private fun reading(
    step: RunnerStep,
    endSec: Double,
    elapsed: Long,
): Reading =
    when (step) {
        is RunnerStep.Hold -> {
            // The step's own end, which +30s doesn't move. The minimum is marked only when it is short
            // of the end, or the mark and the end would land as two cues in one second.
            val end = step.holdEndSec
            val target = step.holdTargetSec
            val remaining = end?.let { it - elapsed }
            Reading(
                ended = remaining != null && remaining <= 0,
                tick = remaining != null && remaining > 0 && remaining <= TICK_SECONDS,
                milestone = target != null && elapsed >= target && (end == null || target < end),
            )
        }

        is RunnerStep.Rest -> {
            countdown(endSec, elapsed, halfway = null)
        }

        is RunnerStep.Interval -> {
            // Only HIIT work marks its midpoint: its rests already tick, and EMOM and AMRAP are either
            // too short for a midpoint to mean anything or better served by their own cues.
            val halfway = if (step.variant == IntervalVariant.HIIT) step.targetSec / 2 else null
            if (step.countUp) QUIET else countdown(endSec, elapsed, halfway)
        }

        is RunnerStep.Reps -> {
            QUIET
        }
    }

private fun countdown(
    endSec: Double,
    elapsed: Long,
    halfway: Double?,
): Reading {
    val remaining = max(0.0, endSec - elapsed)
    return Reading(
        ended = remaining <= 0,
        tick = remaining <= TICK_SECONDS,
        milestone = halfway != null && remaining <= halfway,
    )
}

/** The cues [reading] asks for that this visit hasn't had: the milestone once, a tick once a second. */
private fun cued(
    state: RunnerState,
    reading: Reading,
    elapsed: Long,
): RunnerTransition {
    val marks = state.cues
    val milestone = reading.milestone && !marks.milestone
    val tick = reading.tick && marks.second != elapsed
    val next = CueMarks(elapsed, marks.milestone || milestone)
    val cues = listOfNotNull(RunnerCue.MILESTONE.takeIf { milestone }, RunnerCue.TICK.takeIf { tick })
    val effects = cues.map(RunnerEffect::PlayCue)
    return if (next == marks) unchanged(state) else RunnerTransition(state.copy(cues = next), effects)
}
