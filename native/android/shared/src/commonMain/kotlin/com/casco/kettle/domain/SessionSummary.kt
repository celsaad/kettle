package com.casco.kettle.domain

import kotlin.time.Instant

/**
 * How long [session] ran, in whole minutes: `sessionDurationMinutes`,
 * held by `conformance/records.yaml`. An unfinished
 * session is 0, and so is one that ended before it started, which a clock set back mid-session does.
 *
 * Rounded as JS's `Math.round`, through [roundHalfUp]. A time that isn't an instant counts as 0: nothing
 * the app writes is one, but a hand-edited file can be.
 */
fun sessionDurationMinutes(session: Session): Int {
    val started = session.startedInstant()
    val ended = session.endedAt?.let(::instantOrNull)
    if (started == null || ended == null) return 0
    val minutes = (ended - started).inWholeMilliseconds / MS_PER_MINUTE
    return maxOf(0, roundHalfUp(minutes).toInt())
}

/**
 * Whether [session]'s [sessionSetCount] is rounds: a session of intervals alone, HIIT, AMRAP and EMOM, with
 * rests not counted. Anything else in it keeps the count in sets, since a round of three moves and a set of
 * eight reps would add up to nothing. History's rows (#483) and the finish receipt (#520) both say it.
 */
fun sessionInRounds(session: Session): Boolean {
    val worked = session.entries.filter { it !is SessionEntry.Rest }
    return worked.isNotEmpty() &&
        worked.all { it is SessionEntry.Hiit || it is SessionEntry.Amrap || it is SessionEntry.Emom }
}

/**
 * How many sets [session] is worth: `sessionSetCount`,
 * held by `conformance/records.yaml`. The unit is one interval actually performed, so a HIIT or
 * AMRAP round and an EMOM minute each count one; cardio is one continuous effort, and rest is none.
 */
fun sessionSetCount(session: Session): Int =
    session.entries.sumOf { entry ->
        when (entry) {
            is SessionEntry.Reps -> entry.sets.size
            is SessionEntry.TimedHold -> entry.sets.size
            is SessionEntry.Hiit -> entry.roundsCompleted.toInt()
            is SessionEntry.Amrap -> entry.roundsCompleted.toInt()
            is SessionEntry.Emom -> entry.minutes.size
            is SessionEntry.Cardio -> 1
            is SessionEntry.Rest -> 0
        }
    }

/** When [Session.startedAt] is, or null where it isn't an instant, so a hand-edited time is left out. */
internal fun Session.startedInstant(): Instant? = instantOrNull(startedAt)

private fun instantOrNull(text: String): Instant? =
    try {
        Instant.parse(text)
    } catch (_: IllegalArgumentException) {
        null
    }

private const val MS_PER_MINUTE = 60_000.0

/**
 * What one logged entry put up: structured, never a
 * sentence, which History's screen renders. Seven entry types fold onto six shapes, since hiit and
 * amrap both reduce to rounds.
 */
sealed interface EntryResult {
    data class Holds(
        val holdSecs: List<Double>,
    ) : EntryResult

    /** [weightsKg] is each set's load, null for one logged without, beside [reps] set for set. */
    data class Reps(
        val reps: List<Long>,
        val weightsKg: List<Double?>,
    ) : EntryResult

    data class Rounds(
        val rounds: Long,
        val extraReps: Long?,
    ) : EntryResult

    /** [totalReps] is null where the minutes add up to no reps, entered or not, rather than a total of 0. */
    data class Intervals(
        val intervals: Int,
        val totalReps: Long?,
    ) : EntryResult

    data class Cardio(
        val durationSec: Double?,
        val distanceMeters: Double?,
    ) : EntryResult

    data class Rest(
        val restTakenSec: Double,
    ) : EntryResult
}

/**
 * What an entry put up, held by `conformance/records.yaml`: History's rows, the session editor, Exercise
 * detail's history and the finish screen's "against last time" all read it, so one entry can't say two
 * things on two screens.
 */
fun sessionEntryResult(entry: SessionEntry): EntryResult =
    when (entry) {
        is SessionEntry.TimedHold -> {
            EntryResult.Holds(entry.sets.map { it.holdSec })
        }

        is SessionEntry.Reps -> {
            EntryResult.Reps(entry.sets.map { it.reps }, entry.sets.map { it.weightKg })
        }

        is SessionEntry.Hiit -> {
            EntryResult.Rounds(entry.roundsCompleted, null)
        }

        is SessionEntry.Emom -> {
            val totalReps = entry.minutes.sumOf { it.reps ?: 0L }
            EntryResult.Intervals(entry.minutes.size, totalReps.takeIf { it != 0L })
        }

        is SessionEntry.Amrap -> {
            EntryResult.Rounds(entry.roundsCompleted, entry.extraReps)
        }

        is SessionEntry.Cardio -> {
            EntryResult.Cardio(entry.durationSec, entry.distanceMeters)
        }

        is SessionEntry.Rest -> {
            EntryResult.Rest(entry.restTakenSec)
        }
    }
