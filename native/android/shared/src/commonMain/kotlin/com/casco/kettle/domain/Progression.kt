package com.casco.kettle.domain

// "Try 53.5 lb" (#410): the next load for double progression, suggested only, never applied.

/**
 * The load to try on [step]: last session's load plus a step, once every set last session reached the
 * top of the rep range. Null when there is nothing to suggest: no range (no top to reach), a set short
 * of it, a bodyweight set, no finished session to go on, or a program week that sets this exercise's
 * load itself ([weightSetBy]), which wins over a suggestion.
 *
 * The step is the user's own last increase, read from history, else the smallest jump the equipment
 * has in [units] (`weightStep`), so it follows how they actually load the bar. [priorSessions] newest
 * first, as `previousSetFor` takes them.
 */
fun progressionLoadKg(
    step: RunnerStep.Reps,
    priorSessions: List<Session>,
    units: UnitSystem,
    weightSetBy: Set<String> = emptySet(),
): Double? {
    val top = step.targetRepsMax
    val loads = workingLoads(priorSessions, step.exerciseId)
    val last = lastEntry(priorSessions, step.exerciseId)
    val reachedTop = top != null && last != null && last.sets.isNotEmpty() && last.sets.all { it.reps >= top }
    val lastKg =
        last
            ?.sets
            ?.lastOrNull()
            ?.weightKg
            ?.takeIf { it > 0 }
    if (step.exerciseId in weightSetBy || !reachedTop || lastKg == null) return null
    val learned = loads.zipWithNext().firstNotNullOfOrNull { (newer, older) -> (newer - older).takeIf { it > 0 } }
    return lastKg + (learned ?: fromDisplayWeight(weightStep(units), units))
}

/** [exerciseId]'s reps entry in the newest finished session that has one. */
private fun lastEntry(
    sessions: List<Session>,
    exerciseId: String,
): SessionEntry.Reps? = finishedEntries(sessions, exerciseId).firstOrNull()

/** Each finished session's heaviest set of [exerciseId], newest first: the load that session worked at. */
private fun workingLoads(
    sessions: List<Session>,
    exerciseId: String,
): List<Double> =
    finishedEntries(sessions, exerciseId)
        .mapNotNull { entry -> entry.sets.mapNotNull { it.weightKg }.maxOrNull() }
        .toList()

private fun finishedEntries(
    sessions: List<Session>,
    exerciseId: String,
): Sequence<SessionEntry.Reps> =
    sessions
        .asSequence()
        .filter { it.endedAt != null }
        .mapNotNull { session ->
            session.entries.filterIsInstance<SessionEntry.Reps>().firstOrNull {
                it.exercise ==
                    exerciseId
            }
        }
