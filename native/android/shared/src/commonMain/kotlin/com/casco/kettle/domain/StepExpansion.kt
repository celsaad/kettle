package com.casco.kettle.domain

import kotlin.math.floor

// One exercise expanded into its steps, for `RunnerSteps.kt`'s workout expansion and for a
// mid-session substitute or ad-hoc pick, so all three build a given exercise the same way.

/**
 * Clamps the counts of one exercise and remembers whether any was cut, as it happens, rather than a
 * second copy of the arithmetic recomputing it: `sets: 3000` clamps to 3999 steps, far under the
 * workout ceiling, so the list's length can't say it was truncated.
 */
private class Clamp {
    var truncated = false
        private set

    /** As TS's `Math.min(Math.max(0, Math.floor(count)), max)`, NaN included: it runs nothing. */
    fun bounded(count: Double): Long {
        val next = minOf(maxOf(0.0, floor(count)), MAX_STEPS_PER_EXERCISE.toDouble())
        if (next < count) truncated = true
        return if (next.isNaN()) 0 else next.toLong()
    }

    fun bounded(count: Long): Long = bounded(count.toDouble())
}

/** The fields every step of one expansion shares. */
private data class Origin(
    val exercise: Exercise,
    val blockIndex: Int,
    val memberKey: String,
) {
    fun rest(seconds: Double): RunnerStep.Rest =
        RunnerStep.Rest(blockIndex, memberKey, exercise.id, standalone = false, seconds = seconds)
}

internal fun expandExercise(
    exercise: Exercise,
    blockIndex: Int,
    memberKey: String,
    configOverride: BlockConfigOverride? = null,
    visit: CircuitVisit? = null,
): StepBuildResult {
    val origin = Origin(exercise, blockIndex, memberKey)
    val clamp = Clamp()
    val steps =
        when (val config = exercise.config) {
            is ExerciseConfig.TimedHold -> {
                holdSteps(origin, config, clamp, visit)
            }

            is ExerciseConfig.Reps -> {
                repsSteps(origin, config, clamp, visit)
            }

            is ExerciseConfig.Hiit -> {
                hiitSteps(origin, config, clamp)
            }

            is ExerciseConfig.Emom -> {
                emomSteps(origin, config, clamp)
            }

            is ExerciseConfig.Amrap -> {
                listOf(interval(origin, IntervalVariant.AMRAP, config.timeCapSec, 1, 1))
            }

            is ExerciseConfig.Cardio -> {
                listOf(cardioStep(origin, config))
            }

            is ExerciseConfig.Rest -> {
                val seconds = configOverride?.durationSec ?: config.durationSec
                listOf(RunnerStep.Rest(blockIndex, memberKey, exercise.id, standalone = true, seconds = seconds))
            }
        }
    return StepBuildResult(steps, clamp.truncated)
}

/** `sets` work steps, each built by [step] from its 1-based index and total, with rest between them. */
private fun sets(
    origin: Origin,
    count: Long,
    restSec: Double,
    visit: CircuitVisit?,
    step: (index: Long, total: Long) -> RunnerStep,
): List<RunnerStep> =
    buildList {
        for (i in 0 until count) {
            add(step(visit?.index ?: (i + 1), visit?.total ?: count))
            if (visit == null && i < count - 1 && restSec > 0) add(origin.rest(restSec))
        }
    }

private fun holdSteps(
    origin: Origin,
    config: ExerciseConfig.TimedHold,
    clamp: Clamp,
    visit: CircuitVisit?,
): List<RunnerStep> {
    val count = if (visit != null) 1 else clamp.bounded(config.sets)
    // A 0 or negative end would auto-advance on the first tick and skip the hold, so it degrades to no
    // end at all: the set can still be run by hand, as before holds ended themselves.
    val holdEndSec = (config.holdSecMax ?: config.holdSecMin)?.takeIf { it > 0 }
    return sets(origin, count, config.restSec, visit) { index, total ->
        RunnerStep.Hold(
            origin.blockIndex,
            origin.memberKey,
            origin.exercise.id,
            origin.exercise.name,
            holdTargetSec = config.holdSecMin,
            holdTargetMaxSec = config.holdSecMax,
            holdEndSec = holdEndSec,
            setIndex = index,
            setTotal = total,
            notes = origin.exercise.notes,
        )
    }
}

private fun repsSteps(
    origin: Origin,
    config: ExerciseConfig.Reps,
    clamp: Clamp,
    visit: CircuitVisit?,
): List<RunnerStep> {
    val count = if (visit != null) 1 else clamp.bounded(config.sets)
    return sets(origin, count, config.restSec, visit) { index, total ->
        RunnerStep.Reps(
            origin.blockIndex,
            origin.memberKey,
            origin.exercise.id,
            origin.exercise.name,
            targetReps = config.targetRepsMin,
            targetRepsMax = config.targetRepsMax,
            targetWeightKg = config.targetWeightKg,
            setIndex = index,
            setTotal = total,
            notes = origin.exercise.notes,
        )
    }
}

private fun interval(
    origin: Origin,
    variant: IntervalVariant,
    targetSec: Double,
    index: Long,
    total: Long,
): RunnerStep.Interval =
    RunnerStep.Interval(
        origin.blockIndex,
        origin.memberKey,
        origin.exercise.id,
        origin.exercise.name,
        variant = variant,
        targetSec = targetSec,
        countUp = false,
        setIndex = index,
        setTotal = total,
        notes = origin.exercise.notes,
    )

private fun hiitSteps(
    origin: Origin,
    config: ExerciseConfig.Hiit,
    clamp: Clamp,
): List<RunnerStep> {
    // No visit here on purpose: a HIIT member runs its own rounds on every circuit visit.
    val rounds = clamp.bounded(config.rounds)
    return sets(origin, rounds, config.restSec, visit = null) { index, total ->
        interval(origin, IntervalVariant.HIIT, config.workSec, index, total)
    }
}

/**
 * One interval per `interval_sec` of the block's total duration, floored *before* the clamp: the
 * floor isn't truncation (13⅓ intervals is 13), and reporting it as such put the "too long" screen
 * before ordinary workouts. At least one, so an interval longer than the block still runs.
 */
private fun emomSteps(
    origin: Origin,
    config: ExerciseConfig.Emom,
    clamp: Clamp,
): List<RunnerStep> {
    val count = clamp.bounded(maxOf(1.0, emomIntervalCount(config.intervalSec, config.totalMinutes)))
    return (1..count).map { index ->
        interval(origin, IntervalVariant.EMOM, config.intervalSec, index, count).copy(targetReps = config.targetReps)
    }
}

private fun cardioStep(
    origin: Origin,
    config: ExerciseConfig.Cardio,
): RunnerStep.Interval =
    interval(origin, IntervalVariant.CARDIO, config.durationSec ?: 0.0, 1, 1)
        .copy(countUp = config.durationSec == null, cardioDistanceMeters = config.distanceMeters)
