package com.casco.kettle.domain

import kotlinx.datetime.Instant

/**
 * How long [session] ran, in whole minutes: `sessionDurationMinutes` in
 * `src/state/selectors/session-summary.ts`, held to it by `conformance/records.yaml`. An unfinished
 * session is 0, and so is one that ended before it started, which a clock set back mid-session does.
 *
 * Rounded as JS's `Math.round`, through [roundHalfUp]. A time that isn't an instant counts as 0 where RN's would
 * be NaN; nothing RN writes is either.
 */
fun sessionDurationMinutes(session: Session): Int {
    val started = session.startedInstant()
    val ended = session.endedAt?.let(::instantOrNull)
    if (started == null || ended == null) return 0
    val minutes = (ended - started).inWholeMilliseconds / MS_PER_MINUTE
    return maxOf(0, roundHalfUp(minutes).toInt())
}

/**
 * How many sets [session] is worth: `sessionSetCount` in `src/state/selectors/session-summary.ts`,
 * held to it by `conformance/records.yaml`. The unit is one interval actually performed, so a HIIT or
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

/** When [Session.startedAt] is, or null where it isn't an instant, as RN's invalid `Date` is left out. */
internal fun Session.startedInstant(): Instant? = instantOrNull(startedAt)

private fun instantOrNull(text: String): Instant? =
    try {
        Instant.parse(text)
    } catch (_: IllegalArgumentException) {
        null
    }

private const val MS_PER_MINUTE = 60_000.0

/**
 * What one logged entry put up, as `EntryResult` in `src/domain/format.ts`: structured, never a
 * sentence, which History's screen renders. Seven entry types fold onto six shapes, since hiit and
 * amrap both reduce to rounds.
 */
sealed interface EntryResult {
    data class Holds(
        val holdSecs: List<Double>,
    ) : EntryResult

    data class Reps(
        val reps: List<Long>,
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
 * `sessionEntryResult` in `src/state/selectors/session-summary.ts`, held to it by
 * `conformance/records.yaml`: RN's session editor keeps reading it until M5.
 */
fun sessionEntryResult(entry: SessionEntry): EntryResult =
    when (entry) {
        is SessionEntry.TimedHold -> {
            EntryResult.Holds(entry.sets.map { it.holdSec })
        }

        is SessionEntry.Reps -> {
            EntryResult.Reps(entry.sets.map { it.reps })
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
