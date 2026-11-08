package com.casco.kettle.domain

/**
 * What a logged entry competes on: `RecordKind` in `src/state/selectors/records.ts`, which holds the
 * reasoning. [json] is the kind as RN spells it, which [recordKey] writes into every key.
 */
enum class RecordKind(
    val json: String,
) {
    HeaviestSet("heaviestSet"),
    MostReps("mostReps"),
    LongestHold("longestHold"),
    MostRounds("mostRounds"),
}

/** The single number an entry puts up for a record, and which record it competes for. */
data class EntryBest(
    val kind: RecordKind,
    val value: Double,
    val reps: Long,
    val oneRepMaxKg: Double?,
)

/**
 * `entryBest` in `src/state/selectors/records.ts`, held to it by `conformance/records.yaml`. Null for
 * an entry that competes for nothing: interval work and rest always, and a set list with no sets.
 */
fun entryBest(entry: SessionEntry): EntryBest? =
    when (entry) {
        is SessionEntry.Reps -> {
            repsBest(entry.sets)
        }

        is SessionEntry.TimedHold -> {
            entry.sets.maxOfOrNull { it.holdSec }?.let { EntryBest(RecordKind.LongestHold, it, 0, null) }
        }

        is SessionEntry.Amrap -> {
            EntryBest(RecordKind.MostRounds, entry.roundsCompleted.toDouble(), 0, null)
        }

        is SessionEntry.Hiit, is SessionEntry.Emom, is SessionEntry.Cardio, is SessionEntry.Rest -> {
            null
        }
    }

/** Split on whether a load was logged at all, not on its size, as RN's is. */
private fun repsBest(sets: List<RepsSetLog>): EntryBest? {
    val loaded = sets.filter { (it.weightKg ?: 0.0) > 0 }
    return when {
        sets.isEmpty() -> {
            null
        }

        loaded.isEmpty() -> {
            EntryBest(RecordKind.MostReps, sets.maxOf { it.reps }.toDouble(), 0, null)
        }

        else -> {
            // The first of equals, as RN's reduce keeps it.
            val heaviest = loaded.reduce { best, set -> if (set.weightKg!! > best.weightKg!!) set else best }
            val oneRepMaxKg = loaded.mapNotNull { estimatedOneRepMaxKg(it.weightKg, it.reps) }.maxOrNull()
            EntryBest(RecordKind.HeaviestSet, heaviest.weightKg!!, heaviest.reps, oneRepMaxKg)
        }
    }
}

/**
 * Every best's key, by exercise and kind. Serialized as RN's `JSON.stringify([id, kind])` rather than
 * joined: ids are hand-written, so any separator is one two ids could collide on.
 */
fun recordKey(
    exerciseId: String,
    kind: RecordKind,
): String = "[${jsonString(exerciseId)},${jsonString(kind.json)}]"

/**
 * Epley, `src/domain/one-rm.ts`, which holds the reasoning: null for a bodyweight set, no reps, or
 * past [MAX_ESTIMABLE_REPS], and a single as itself.
 */
fun estimatedOneRepMaxKg(
    weightKg: Double?,
    reps: Long,
): Double? =
    when {
        weightKg == null || weightKg <= 0 -> null
        reps < 1 || reps > MAX_ESTIMABLE_REPS -> null
        reps == 1L -> weightKg
        else -> weightKg * (1 + reps / EPLEY_REPS)
    }

private const val MAX_ESTIMABLE_REPS = 12
private const val EPLEY_REPS = 30.0

/**
 * What a session beat, for the completion screen: `sessionRecords` in `src/state/selectors/records.ts`,
 * held to it by `conformance/records.yaml`. [exerciseName] is the user's own and renders verbatim.
 */
sealed interface SessionRecord {
    val kind: RecordKind
    val exerciseId: String
    val exerciseName: String

    /** [oneRepMaxKg] is the best estimate across the entry, which is not always the heaviest set's. */
    data class HeaviestSet(
        override val exerciseId: String,
        override val exerciseName: String,
        val weightKg: Double,
        val reps: Long,
        val oneRepMaxKg: Double?,
    ) : SessionRecord {
        override val kind get() = RecordKind.HeaviestSet
    }

    data class MostReps(
        override val exerciseId: String,
        override val exerciseName: String,
        val reps: Double,
    ) : SessionRecord {
        override val kind get() = RecordKind.MostReps
    }

    data class LongestHold(
        override val exerciseId: String,
        override val exerciseName: String,
        val holdSec: Double,
    ) : SessionRecord {
        override val kind get() = RecordKind.LongestHold
    }

    data class MostRounds(
        override val exerciseId: String,
        override val exerciseName: String,
        val rounds: Double,
    ) : SessionRecord {
        override val kind get() = RecordKind.MostRounds
    }
}

/**
 * What [session] beat across the finished sessions in [priorSessions]: strictly greater, so a tie is no
 * record, and only against a prior best, so a first-ever entry is none either. [session] is skipped in
 * [priorSessions], since a caller reads both from the one log. One record per exercise and kind, the
 * best of them, when one exercise was logged in two blocks.
 */
fun sessionRecords(
    session: Session,
    priorSessions: List<Session>,
    exercises: List<Exercise>,
): List<SessionRecord> {
    val before = bestByKey(priorSessions.filter { it.id != session.id })
    val beaten =
        session.entries
            .mapNotNull { entry -> entryBest(entry)?.let { entry.exercise to it } }
            .filter { (exercise, best) -> before[recordKey(exercise, best.kind)]?.let { best.value > it } == true }
    // The first of equals, as RN's keeps the record it collected first.
    val strongest = linkedMapOf<String, Pair<String, EntryBest>>()
    for ((exercise, best) in beaten) {
        val key = recordKey(exercise, best.kind)
        if ((strongest[key]?.second?.value ?: Double.NEGATIVE_INFINITY) < best.value) strongest[key] = exercise to best
    }
    return strongest.values.map { (exercise, best) -> recordOf(exercise, exerciseName(exercises, exercise), best) }
}

/** Every finished session's best per exercise and kind, by [recordKey]. */
private fun bestByKey(sessions: List<Session>): Map<String, Double> =
    sessions
        .filter { it.endedAt != null }
        .flatMap { it.entries }
        .mapNotNull { entry -> entryBest(entry)?.let { recordKey(entry.exercise, it.kind) to it.value } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.max() }

private fun recordOf(
    id: String,
    name: String,
    best: EntryBest,
): SessionRecord =
    when (best.kind) {
        RecordKind.HeaviestSet -> SessionRecord.HeaviestSet(id, name, best.value, best.reps, best.oneRepMaxKg)
        RecordKind.MostReps -> SessionRecord.MostReps(id, name, best.value)
        RecordKind.LongestHold -> SessionRecord.LongestHold(id, name, best.value)
        RecordKind.MostRounds -> SessionRecord.MostRounds(id, name, best.value)
    }
