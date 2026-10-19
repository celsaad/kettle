package com.casco.kettle.domain

/**
 * One logged session, the domain model of a session file, as `Session` in `src/domain/types.ts`.
 *
 * An optional field is a nullable property whose null means *absent*, and a `| null` field is a
 * nullable property whose null means null. No field is both, so the two never need telling apart in
 * a value. They differ only on the way in and out, which is `SessionYaml.kt`'s business.
 */
data class Session(
    val version: Long,
    val id: String,
    val workout: String?,
    /** The program this session was started under (via a program week), if any. */
    val program: String?,
    /** Which week/day of [program] this was, if started via a program week; null for a plain workout. */
    val programWeek: Long?,
    val programDay: String?,
    val startedAt: String,
    val endedAt: String?,
    val entries: List<SessionEntry>,
)

/** What was done for one exercise. Only the shapes the session runner can produce are modelled. */
sealed interface SessionEntry {
    val exercise: String

    data class TimedHold(
        override val exercise: String,
        val sets: List<TimedHoldSetLog>,
    ) : SessionEntry

    data class Reps(
        override val exercise: String,
        val sets: List<RepsSetLog>,
    ) : SessionEntry

    data class Rest(
        override val exercise: String,
        val restTakenSec: Double,
    ) : SessionEntry

    data class Hiit(
        override val exercise: String,
        val roundsCompleted: Long,
    ) : SessionEntry

    data class Emom(
        override val exercise: String,
        val minutes: List<EmomMinuteLog>,
    ) : SessionEntry

    data class Amrap(
        override val exercise: String,
        val roundsCompleted: Long,
        val extraReps: Long? = null,
    ) : SessionEntry

    data class Cardio(
        override val exercise: String,
        val durationSec: Double? = null,
        val distanceMeters: Double? = null,
    ) : SessionEntry
}

data class TimedHoldSetLog(
    val holdSec: Double,
    val restTakenSec: Double,
)

/** [weightKg] is kilograms whatever the reader's unit setting, as every stored weight is. */
data class RepsSetLog(
    val reps: Long,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val restTakenSec: Double,
)

data class EmomMinuteLog(
    val reps: Long? = null,
)
