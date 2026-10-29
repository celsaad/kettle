package com.casco.kettle.domain

// `src/domain/logged-work.ts` and `src/state/selectors/logged-sessions.ts`, which hold the reasoning:
// logging 0 is how an exercise is skipped in the runner, so a set at exactly 0 reps or 0 seconds is
// "not done", and a finished session left with no work is hidden from every training view. EMOM and
// HIIT are deliberately out. The files keep what was written; this filters on read.

/**
 * [entry] with its zero work taken out, or null when nothing of it is left. The same object when
 * there was nothing to remove, which keeps [withoutZeroWork] identity-stable.
 */
fun entryWithoutZeroWork(entry: SessionEntry): SessionEntry? =
    when (entry) {
        is SessionEntry.Reps -> {
            nonZero(entry.sets) { it.reps == 0L }?.let { if (it === entry.sets) entry else entry.copy(sets = it) }
        }

        is SessionEntry.TimedHold -> {
            nonZero(entry.sets) { it.holdSec == 0.0 }?.let { if (it === entry.sets) entry else entry.copy(sets = it) }
        }

        is SessionEntry.Amrap -> {
            entry.takeIf { it.roundsCompleted != 0L || it.extraReps.orZero() != 0L }
        }

        is SessionEntry.Cardio -> {
            entry.takeIf { it.durationSec.orZero() != 0.0 || it.distanceMeters.orZero() != 0.0 }
        }

        is SessionEntry.Rest, is SessionEntry.Hiit, is SessionEntry.Emom -> {
            entry
        }
    }

/** [sets] less the zero ones: the same list when none were, and null when none are left. */
private fun <S> nonZero(
    sets: List<S>,
    isZero: (S) -> Boolean,
): List<S>? {
    val kept = sets.filterNot(isZero)
    return when (kept.size) {
        0 -> null
        sets.size -> sets
        else -> kept
    }
}

private fun Long?.orZero() = this ?: 0L

private fun Double?.orZero() = this ?: 0.0

/** [session] with every zero set, and every entry left empty by that, removed. The same object if none was. */
fun withoutZeroWork(session: Session): Session {
    val entries = session.entries.mapNotNull(::entryWithoutZeroWork)
    val changed = entries.size != session.entries.size || entries.indices.any { entries[it] !== session.entries[it] }
    return if (changed) session.copy(entries = entries) else session
}

/** Whether [session] holds any work: a standalone rest doesn't count, since skipped workouts still log them. */
fun hasLoggedWork(session: Session): Boolean = session.entries.any { it !is SessionEntry.Rest }

/**
 * The log as every training view should see it: zero sets gone, and a finished session with no work
 * left hidden outright. An in-flight session is never hidden, since it starts out empty.
 */
fun loggedSessions(sessions: List<Session>): List<Session> =
    sessions.mapNotNull { session ->
        withoutZeroWork(session).takeUnless { it.endedAt != null && !hasLoggedWork(it) }
    }
