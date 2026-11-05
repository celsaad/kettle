package com.casco.kettle.domain

// "Last time" on the set row and the live PR marker beside it (§3.3 of #294's inventory), from
// `previousSetFor` and `personalBestFor` in RN's `src/state/selectors/records.ts`, which hold the
// reasoning. Both read the log as it stood when the session started, `SessionLog.logged`, never the
// live one, which would answer "last time" with the set finished two minutes ago.

/** One set of a previously logged entry, as data: the weight can't be text until it is in display units. */
sealed interface PreviousSet {
    data class Reps(
        val reps: Long,
        val weightKg: Double?,
    ) : PreviousSet

    data class Hold(
        val holdSec: Double,
    ) : PreviousSet
}

/** Null for a kind never logged: "no best yet" is not "a best of nothing". */
data class PersonalBest(
    val heaviestSetKg: Double?,
    val mostReps: Double?,
    val longestHoldSec: Double?,
)

/**
 * What [exerciseId]'s set [setIndex] was the last time it was trained, newest first, skipping
 * unfinished sessions. A shorter entry falls back to its last set. Only reps and holds have sets to show.
 */
fun previousSetFor(
    sessions: List<Session>,
    exerciseId: String,
    setIndex: Long,
): PreviousSet? {
    val entry =
        sessions
            .asSequence()
            .filter { it.endedAt != null }
            .flatMap { it.entries }
            .firstOrNull { it.exercise == exerciseId && (it is SessionEntry.Reps || it is SessionEntry.TimedHold) }
    val at = (setIndex - 1).toInt()
    return when (entry) {
        is SessionEntry.Reps -> {
            (entry.sets.getOrNull(at) ?: entry.sets.lastOrNull())?.let {
                PreviousSet.Reps(it.reps, it.weightKg)
            }
        }

        is SessionEntry.TimedHold -> {
            (
                entry.sets.getOrNull(
                    at,
                ) ?: entry.sets.lastOrNull()
            )?.let { PreviousSet.Hold(it.holdSec) }
        }

        else -> {
            null
        }
    }
}

/** [exerciseId]'s best ever, by the rules the completion screen's records use: [entryBest], finished sessions only. */
fun personalBestFor(
    sessions: List<Session>,
    exerciseId: String,
): PersonalBest {
    val bests =
        sessions
            .filter { it.endedAt != null }
            .flatMap { it.entries }
            .filter { it.exercise == exerciseId }
            .mapNotNull(::entryBest)
    val best = { kind: RecordKind -> bests.filter { it.kind == kind }.maxOfOrNull { it.value } }
    return PersonalBest(best(RecordKind.HeaviestSet), best(RecordKind.MostReps), best(RecordKind.LongestHold))
}

/** "Last time" for the step on screen, matched on its set number; null off a reps or hold step. */
fun previousSet(
    snapshot: RunnerSnapshot,
    priorSessions: List<Session>,
): PreviousSet? =
    when (val step = snapshot.step) {
        is RunnerStep.Reps, is RunnerStep.Hold -> {
            previousSetFor(
                priorSessions,
                step.exerciseId,
                (step as WorkStep).setIndex,
            )
        }

        else -> {
            null
        }
    }

/**
 * Whether what's on screen at [now] beats everything ever logged for the exercise: prospective,
 * strictly greater, and raised by this member's own sets so far, so three sets at a new top weight
 * mark only the first. Nothing to beat is no record. Bodyweight competes on reps, loaded on load.
 */
fun beatsPersonalBest(
    state: RunnerState,
    priorSessions: List<Session>,
    now: Long,
): Boolean {
    val step = state.snapshot.step
    val inputs = state.snapshot.inputs
    val best = (step as? WorkStep)?.let { personalBestFor(priorSessions, it.exerciseId) }
    val logged = step?.let { state.memberEntry(it.memberKey) }
    // As RN's `raise`: the session's own sets set a bar even with no history, so a heavier second set
    // of a first-ever session marks.
    val beats = { value: Double, best: Double?, own: List<Double> ->
        listOfNotNull(best, own.maxOrNull()).maxOrNull()?.let { value > it } ?: false
    }
    return when {
        best == null -> {
            false
        }

        step is RunnerStep.Hold -> {
            val holds = (logged as? SessionEntry.TimedHold)?.sets.orEmpty().map { it.holdSec }
            beats(elapsedSec(state.snapshot.clock, now).toDouble(), best.longestHoldSec, holds)
        }

        step !is RunnerStep.Reps -> {
            false
        }

        inputs.weightKg > 0 -> {
            val loads =
                (logged as? SessionEntry.Reps)?.sets.orEmpty().mapNotNull {
                    it.weightKg?.takeIf { kg ->
                        kg > 0
                    }
                }
            beats(inputs.weightKg, best.heaviestSetKg, loads)
        }

        else -> {
            val reps =
                (logged as? SessionEntry.Reps)
                    ?.sets
                    .orEmpty()
                    .filter {
                        (it.weightKg ?: 0.0) <= 0
                    }.map { it.reps.toDouble() }
            beats(inputs.reps.toDouble(), best.mostReps, reps)
        }
    }
}
