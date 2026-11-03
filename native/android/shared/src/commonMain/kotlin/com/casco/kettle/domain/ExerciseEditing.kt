package com.casco.kettle.domain

import kotlinx.datetime.Instant

// What the exercise editor (#241) does to the library, and what it shows of the log. The library
// edits were RN's library store's `saveExercise` and `deleteExercise`; the rest is
// `history-views.ts`' `exerciseHistory`.

/** The library with [exercise] in it: in place of the one with its id, or else at the end. */
fun Library.withExercise(exercise: Exercise): Library {
    val at = exercises.indexOfFirst { it.id == exercise.id }
    val edited = if (at < 0) exercises + exercise else exercises.toMutableList().apply { set(at, exercise) }
    return copy(exercises = edited)
}

/** The library without exercise [id]. Nothing checks it's unused: that's [workoutsUsing]'s, first. */
fun Library.withoutExercise(id: String): Library = copy(exercises = exercises.filterNot { it.id == id })

/**
 * The workouts that use exercise [id], as a plain block or inside a circuit, which deleting it would
 * leave pointing at nothing. The circuit is the case a quick check misses.
 */
fun workoutsUsing(
    library: Library,
    id: String,
): List<Workout> =
    library.workouts.filter { workout ->
        workout.blocks.any { block ->
            when (block) {
                is WorkoutBlock.ExerciseBlock -> block.exerciseId == id
                is WorkoutBlock.Circuit -> block.members.any { it.exerciseId == id }
            }
        }
    }

/** One time the exercise was logged. [startedAt] is null where `started_at` isn't an instant. */
data class ExerciseHistoryEntry(
    val sessionId: String,
    val startedAt: Instant?,
    val result: EntryResult,
    val volume: Double,
)

/** How many past sessions the editor's Recent shows. */
const val RECENT_LIMIT = 5

/**
 * The last [limit] times exercise [id] was logged, newest first as [sessions] already are. Unfinished
 * sessions are skipped, and so are rest entries: a rest's repeat performance means nothing the way a
 * lift's does, so a rest exercise has no history to show.
 */
fun exerciseHistory(
    sessions: List<Session>,
    id: String,
    limit: Int = RECENT_LIMIT,
): List<ExerciseHistoryEntry> =
    sessions
        .asSequence()
        .filter { it.endedAt != null }
        .flatMap { session ->
            session.entries
                .asSequence()
                .filter { it.exercise == id && it !is SessionEntry.Rest }
                .map {
                    ExerciseHistoryEntry(
                        session.id,
                        session.startedInstant(),
                        sessionEntryResult(it),
                        entryVolume(it),
                    )
                }
        }.take(limit)
        .toList()

/**
 * One number for how much an entry did, for the Recent chart to compare sessions by. Reps are weighted
 * by load only when a set carries one, so a bodyweight exercise isn't all zeros; cardio prefers
 * distance, and falls back to duration.
 */
fun entryVolume(entry: SessionEntry): Double =
    when (entry) {
        is SessionEntry.TimedHold -> {
            entry.sets.sumOf { it.holdSec }
        }

        is SessionEntry.Reps -> {
            val weighted = entry.sets.any { it.weightKg != null }
            entry.sets.sumOf { if (weighted) it.reps * (it.weightKg ?: 0.0) else it.reps.toDouble() }
        }

        is SessionEntry.Hiit -> {
            entry.roundsCompleted.toDouble()
        }

        is SessionEntry.Emom -> {
            entry.minutes.sumOf { it.reps ?: 0L }.toDouble()
        }

        is SessionEntry.Amrap -> {
            entry.roundsCompleted.toDouble()
        }

        is SessionEntry.Cardio -> {
            entry.distanceMeters ?: entry.durationSec ?: 0.0
        }

        is SessionEntry.Rest -> {
            0.0
        }
    }
