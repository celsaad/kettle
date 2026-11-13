package com.casco.kettle.domain

// Prev's one-level undo (§2.5 of #294's inventory): the last advance's commit, taken back exactly.
// What it may undo is recorded in `LastCommit`, and every step-list edit clears that record, since its
// index points into the list the edit just changed.

/** How Prev would take back a commit of [step]; null for a rest between sets, which only overwrote a field. */
internal fun undoKind(step: RunnerStep): UndoKind? =
    when (step) {
        is RunnerStep.Hold, is RunnerStep.Reps -> UndoKind.MEMBER
        is RunnerStep.Interval -> if (step.variant.growsAnEntry) UndoKind.MEMBER else UndoKind.ONE_SHOT
        is RunnerStep.Rest -> UndoKind.ONE_SHOT.takeIf { step.standalone }
    }

/** HIIT and EMOM grow the member's entry; AMRAP and cardio log a one-shot entry each. */
private val IntervalVariant.growsAnEntry: Boolean
    get() = this == IntervalVariant.HIIT || this == IntervalVariant.EMOM

/**
 * Takes [commit] back: shrinks the member's entry by the set, round or minute, or retracts the entry
 * and forgets its index when that was all it had, so a redo appends afresh. A one-shot entry is just
 * removed. Either way it is the last entry, since this undoes the most recent commit.
 */
internal fun undoCommit(
    state: RunnerState,
    commit: LastCommit,
): RunnerState {
    val entry = state.memberEntry(commit.memberKey)
    val shrunk = entry?.let(::withoutLastUnit)
    val retracted = state.session.copy(entries = state.session.entries.dropLast(1))
    val forgotten = state.snapshot.copy(entryIndex = state.snapshot.entryIndex - commit.memberKey)
    return when {
        commit.kind == UndoKind.ONE_SHOT -> state.copy(session = retracted)
        entry == null -> state
        shrunk != null -> state.withMemberEntry(commit.memberKey, shrunk)
        else -> state.copy(session = retracted, snapshot = forgotten)
    }
}

/** [entry] less its last set, round or minute; null when that was all it had. */
private fun withoutLastUnit(entry: SessionEntry): SessionEntry? =
    when (entry) {
        is SessionEntry.Reps -> {
            entry.copy(sets = entry.sets.dropLast(1)).takeIf { it.sets.isNotEmpty() }
        }

        is SessionEntry.TimedHold -> {
            entry.copy(sets = entry.sets.dropLast(1)).takeIf { it.sets.isNotEmpty() }
        }

        is SessionEntry.Hiit -> {
            entry
                .copy(
                    roundsCompleted = entry.roundsCompleted - 1,
                ).takeIf { it.roundsCompleted > 0 }
        }

        is SessionEntry.Emom -> {
            entry.copy(minutes = entry.minutes.dropLast(1)).takeIf { it.minutes.isNotEmpty() }
        }

        else -> {
            null
        }
    }

/**
 * The commit Prev would take back now: [RunnerState.lastCommit], but only on the step its advance
 * landed on. One step further on, Prev just moves.
 */
internal fun undoableCommit(state: RunnerState): LastCommit? =
    state.lastCommit?.takeIf { it.resultingIndex == state.snapshot.stepIndex }

/** What an undo would take back, for the runner to name beside its Undo. */
sealed interface LoggedUnit {
    /** A reps or hold set: the member's [number]th, as logged. */
    data class Set(
        val number: Int,
        val set: PreviousSet,
    ) : LoggedUnit

    /** A HIIT round, the member's [number]th. */
    data class Round(
        val number: Long,
    ) : LoggedUnit

    /** An EMOM minute, the member's [number]th. */
    data class Minute(
        val number: Int,
    ) : LoggedUnit

    /** A whole entry (AMRAP, cardio, a standalone rest): [exerciseName], null for a rest. */
    data class Entry(
        val exerciseName: String?,
    ) : LoggedUnit
}

/**
 * What Prev on the step on screen would take back, or null when it would only move: nothing was just
 * logged, an edit has cleared the record since, or the session was rebuilt after process death.
 */
fun lastLogged(state: RunnerState): LoggedUnit? {
    val commit = undoableCommit(state)
    return when {
        commit == null -> {
            null
        }

        commit.kind == UndoKind.ONE_SHOT -> {
            val step = state.snapshot.steps.getOrNull(commit.resultingIndex - 1)
            LoggedUnit.Entry((step as? WorkStep)?.exerciseName)
        }

        else -> {
            loggedUnit(state.memberEntry(commit.memberKey))
        }
    }
}

/** The last unit of a member's [entry]; null for an entry Prev would leave as it is. */
private fun loggedUnit(entry: SessionEntry?): LoggedUnit? =
    when (entry) {
        is SessionEntry.Reps -> {
            entry.sets.lastOrNull()?.let {
                LoggedUnit.Set(entry.sets.size, PreviousSet.Reps(it.reps, it.weightKg))
            }
        }

        is SessionEntry.TimedHold -> {
            entry.sets.lastOrNull()?.let {
                LoggedUnit.Set(entry.sets.size, PreviousSet.Hold(it.holdSec))
            }
        }

        is SessionEntry.Hiit -> {
            LoggedUnit.Round(entry.roundsCompleted)
        }

        is SessionEntry.Emom -> {
            LoggedUnit.Minute(entry.minutes.size)
        }

        else -> {
            null
        }
    }
