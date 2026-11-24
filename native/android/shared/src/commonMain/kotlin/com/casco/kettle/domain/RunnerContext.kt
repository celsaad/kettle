package com.casco.kettle.domain

// What a reps set opens on, from last time: the reps held to the range and the load floored at the
// target, so the counter starts at what the user did rather than at the bottom of the range. The
// reasoning, and the double-progression reset, are in docs/decisions.md ("Last time" on the set row).

/**
 * What the engine reads besides its own state. [priorSessions] is the log as it stood when the session
 * started; the running one has no end, which [previousSetFor] skips. [units] decides whether a load
 * went up as the user reads it, and [loadsSetByProgram] is the plan's. The default is no history,
 * which seeds every set from its target.
 */
data class RunnerContext(
    val priorSessions: List<Session> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
    val loadsSetByProgram: Set<String> = emptySet(),
)

/**
 * The load [step] opens on, first match wins: the member's last set logged today, which the user may
 * have just changed by hand; the program week's own load; last time's load for this set floored at the
 * target, so raising the target in the library always takes; the target. Null with none of them.
 */
fun RunnerState.openingLoadKg(
    step: RunnerStep.Reps,
    context: RunnerContext = RunnerContext(),
): Double? {
    val today = (memberEntry(step.memberKey) as? SessionEntry.Reps)?.sets?.lastOrNull()?.weightKg
    return when {
        today != null -> today
        step.exerciseId in context.loadsSetByProgram -> step.targetWeightKg
        else -> listOfNotNull(lastTimeSet(step, context)?.weightKg, step.targetWeightKg).maxOrNull()
    }
}

/**
 * The reps [step] opens on at [loadKg]: last time's for this set held to the range, or the bottom of
 * the range when there's no last time or the load is heavier than last time's for this set. That reset
 * is double progression's: reach the top, add load, start again from the bottom. Heavier is compared
 * as the user reads it, so a load typed in pounds isn't heavier than itself by the kilogram rounding;
 * bodyweight counts as no load.
 */
fun openingReps(
    step: RunnerStep.Reps,
    loadKg: Double?,
    context: RunnerContext = RunnerContext(),
): Long {
    val last = lastTimeSet(step, context) ?: return step.targetReps
    val heavier =
        toDisplayWeight(loadKg ?: 0.0, context.units) > toDisplayWeight(last.weightKg ?: 0.0, context.units)
    return if (heavier) step.targetReps else repsInRange(step, last.reps)
}

/** [reps] held to [step]'s range; a fixed target is a range of one. */
fun repsInRange(
    step: RunnerStep.Reps,
    reps: Long,
): Long = reps.coerceIn(step.targetReps, maxOf(step.targetReps, step.targetRepsMax ?: step.targetReps))

private fun lastTimeSet(
    step: RunnerStep.Reps,
    context: RunnerContext,
): PreviousSet.Reps? = previousSetFor(context.priorSessions, step.exerciseId, step.setIndex) as? PreviousSet.Reps
