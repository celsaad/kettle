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
