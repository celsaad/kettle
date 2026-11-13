package com.casco.kettle.domain

// "Try 53.5 lb" (#410): the next load for double progression, suggested only, never applied.

/**
 * The load to try on [step]: last session's working load plus a step, once last session logged at
 * least this step's set count and every one of them reached the top of the rep range. Null when there's
 * nothing to suggest: no range (no top to reach), too few sets or one short of the top, a bodyweight
 * session, no finished session to go on, or a program week that sets this exercise's load itself
 * ([loadsSetByProgram]), which wins over a suggestion.
 *
 * A session's working load is its heaviest set, for the base and the step alike, so ramp-up and
 * back-off sets don't pull the two apart. The step is [learnedStepKg], else the smallest jump the
 * equipment has in [units]. [priorSessions] newest first, as `previousSetFor` takes them.
 */
fun progressionLoadKg(
    step: RunnerStep.Reps,
    priorSessions: List<Session>,
    units: UnitSystem,
    loadsSetByProgram: Set<String> = emptySet(),
): Double? {
    val top = step.targetRepsMax
    val last = finishedEntries(priorSessions, step.exerciseId).firstOrNull()
    val reachedTop =
        top != null && last != null && last.sets.size >= step.setTotal && last.sets.all { it.reps >= top }
    val lastKg = last?.let(::workingLoad)?.takeIf { it > 0 }
    if (step.exerciseId in loadsSetByProgram || !reachedTop || lastKg == null) return null
    val loads = finishedEntries(priorSessions, step.exerciseId).mapNotNull(::workingLoad).toList()
    return lastKg + (learnedStepKg(loads) ?: fromDisplayWeight(weightStep(units), units))
}

/**
 * The user's own last progression jump, from working [loads] newest first: the newest session that
 * set a new high over every session before it, less that earlier best. Getting back to a load after a
 * deload isn't a new high, so it teaches nothing, and nor do runs at the same load. Null with none.
 */
internal fun learnedStepKg(loads: List<Double>): Double? =
    loads.indices.firstNotNullOfOrNull { i ->
        val earlierBest = loads.drop(i + 1).maxOrNull()
        earlierBest?.let { loads[i] - it }?.takeIf { it > 0 }
    }

/**
 * [suggestedKg] as Try offers it: only above the load on screen, compared as the user reads them, in
 * [units]. Compared in kilograms, a 53.5 lb typed on the pad and a Try of 53.5 lb can differ by the
 * pound conversion's rounding, which would leave Try showing next to the load it names.
 */
fun offeredLoadKg(
    suggestedKg: Double?,
    onScreenKg: Double,
    units: UnitSystem,
): Double? = suggestedKg?.takeIf { toDisplayWeight(it, units) > toDisplayWeight(onScreenKg, units) }

/** A session's heaviest set of an exercise: the load it worked at. Null for a bodyweight entry. */
private fun workingLoad(entry: SessionEntry.Reps): Double? = entry.sets.mapNotNull { it.weightKg }.maxOrNull()

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
