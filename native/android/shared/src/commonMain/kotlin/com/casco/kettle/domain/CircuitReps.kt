package com.casco.kettle.domain

/**
 * The reps the member logged last round, for a circuit's step from its second round on (#478). Null
 * outside a circuit, in its first round, and whenever the member's sets don't line up one per round
 * before this one: a round skipped at 0 reps writes no set, and a second Back moves without undoing, so
 * the last set can be an older round's. The log has no round number to read instead, so it shows
 * nothing rather than credit one round's count to another.
 */
fun RunnerState.lastRoundReps(step: RunnerStep.Reps): Long? = lastRoundReps(step, memberEntry(step.memberKey))

/** [RunnerState.lastRoundReps] from the member's [entry], for Coming up, which holds the session (#479). */
fun lastRoundReps(
    step: RunnerStep.Reps,
    entry: SessionEntry?,
): Long? =
    // One set per round before this one, so round 1, with none, has nothing to show either.
    step.circuit?.let { circuit ->
        (entry as? SessionEntry.Reps)
            ?.sets
            ?.takeIf { it.size.toLong() == circuit.round - 1 }
            ?.lastOrNull()
            ?.reps
    }
