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
internal fun stepEndSec(step: RunnerStep?): Double =
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
    context: RunnerContext,
): RunnerState {
    val snapshot = state.snapshot
    val step = snapshot.step
    return state.copy(
        snapshot =
            snapshot.copy(
                visit = snapshot.visit + 1,
                clock = StepClock(now, null, 0, stepEndSec(step)),
                inputs = seededInputs(state, step, context),
            ),
        cues = CueMarks(),
    )
}

/**
 * A reps set from last time and its load ([openingLoadKg], [openingReps]), and an EMOM minute from its
 * own target; load left alone off a reps step; rounds and extra reps from zero; effort empty, never
 * carried from the last set (#410).
 */
private fun seededInputs(
    state: RunnerState,
    step: RunnerStep?,
    context: RunnerContext,
): StepInputs {
    val inputs = state.snapshot.inputs
    val loadKg = (step as? RunnerStep.Reps)?.let { state.openingLoadKg(it, context) }
    val reps =
        when (step) {
            is RunnerStep.Reps -> openingReps(step, loadKg, context)
            is RunnerStep.Interval -> step.targetReps ?: 0
            else -> 0
        }
    val weightKg = if (step is RunnerStep.Reps) loadKg ?: 0.0 else inputs.weightKg
    return inputs.copy(reps = reps, weightKg = weightKg, rpe = null, roundsCompleted = 0, extraReps = 0)
}

/** What the clock says about the step on screen at one reading, short of its end. */
private data class Reading(
    val tick: Boolean,
    val milestone: Boolean,
)

private val QUIET = Reading(tick = false, milestone = false)

/** A countdown's last seconds, each of which ticks. Public for the app, whose screen marks them too. */
const val TICK_SECONDS = 3

/**
 * A time update: the step on screen ends by itself, or cues, or neither.
 *
 * - Countdowns (rest, and intervals with a target) end at `remaining <= 0`, holds at the *top* of
 *   their range. A max-effort hold and count-up cardio never end by themselves.
 * - **After a gap it replays the timeline** ([replayed]): each self-ending step ends at its own moment
 *   and the next starts there, through to the first step only the user ends, or the end.
 * - It does nothing when paused, or when aimed at a step already left ([visit] isn't the snapshot's).
 *
 * Ticks sound once per whole second, so a service ticking faster than 1 Hz doesn't play more of them,
 * and a step ending in this update cues only its exercise change.
 */
internal fun timeUpdate(
    state: RunnerState,
    visit: Long,
    now: Long,
    context: RunnerContext,
): RunnerTransition {
    val snapshot = state.snapshot
    val step = snapshot.step
    if (step == null || visit != snapshot.visit || snapshot.clock.paused) return unchanged(state)
    val ended = stepEndAt(snapshot)?.let { it <= now } == true
    val elapsed = elapsedSec(snapshot.clock, now)
    return if (ended) {
        replayed(state, now, context)
    } else {
        cued(state, reading(step, snapshot.clock.stepEndSec, elapsed), elapsed)
    }
}

/**
 * The catch-up (#420): every self-ending step whose end is at or before [now] commits at that end, as a
 * hold at its end or a rest at its planned length, and the next is seeded from there. Committing at the
 * end rather than at [now] is what keeps the time away out of a rest; the step it stops on owns it.
 *
 * Only the last end crossed is announced, with its alert and exercise change, as a live end is: on
 * Android that alert is the only one, so a wake that ran late still says where the session is. The
 * steps before it pass silently, nobody having been there to hear them. An iOS host, whose alerts
 * were posted ahead from [upcomingStepEnds], drops it as [resumeRunner] does. The step it lands on is
 * marked as cued up to now, so a milestone it passed during the gap doesn't sound late. Only the last
 * write is kept, each being the whole file.
 */
private fun replayed(
    state: RunnerState,
    now: Long,
    context: RunnerContext,
): RunnerTransition {
    var current = state
    val crossed = mutableListOf<List<RunnerEffect>>()
    var endAt = stepEndAt(current.snapshot)?.takeIf { it <= now }
    while (endAt != null) {
        val snapshot = current.snapshot
        val moved = advance(current, endAt)
        val next = snapshot.steps.getOrNull(snapshot.stepIndex + 1)
        crossed += listOfNotNull(snapshot.step?.let { RunnerEffect.StepEnded(it, next) }) + moved.effects
        current = if (moved.state.finished) moved.state else seeded(moved.state, endAt, context)
        endAt = stepEndAt(current.snapshot)?.takeIf { it <= now && !current.finished }
    }
    val passed = crossed.dropLast(1).flatten().filterNot { it is RunnerEffect.PlayCue || it is RunnerEffect.StepEnded }
    val effects = passed + crossed.lastOrNull().orEmpty()
    val lastWrite = effects.lastOrNull { it is RunnerEffect.WriteSession }
    val landed = current.takeUnless { it.finished }?.let { markedUpTo(it, now) } ?: current
    return RunnerTransition(landed, effects.filter { it !is RunnerEffect.WriteSession || it === lastWrite })
}

/** [state]'s step on screen with the cues its clock has already passed at [now] marked as had. */
private fun markedUpTo(
    state: RunnerState,
    now: Long,
): RunnerState? {
    val step = state.snapshot.step ?: return null
    val clock = state.snapshot.clock
    val elapsed = elapsedSec(clock, now)
    return state.copy(cues = CueMarks(elapsed, reading(step, clock.stepEndSec, elapsed).milestone))
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
