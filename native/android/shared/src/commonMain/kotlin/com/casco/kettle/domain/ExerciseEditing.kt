package com.casco.kettle.domain

import kotlin.time.Instant

// What the exercise editor (#241) does to the library, and what it shows of the log. The library
// edits were RN's library store's `saveExercise` and `deleteExercise`; the rest is
// `exerciseHistory`.

/** The library with [exercise] in it: in place of the one with its id, or else at the end. */
fun Library.withExercise(exercise: Exercise): Library = copy(exercises = exercises.upserting(exercise, Exercise::id))

/**
 * The library with [weightKg] as `reps` exercise [id]'s target load, or null where there's nothing to
 * set: no such exercise, or one of another type, which has no target weight. The runner's one-tap
 * adopt (#220), with kilograms straight off a logged set, so no display conversion stands between the
 * set and the file.
 */
fun Library.withTargetWeight(
    id: String,
    weightKg: Double,
): Library? {
    val exercise = exercises.firstOrNull { it.id == id && it.config is ExerciseConfig.Reps } ?: return null
    val config = exercise.config as ExerciseConfig.Reps
    return withExercise(exercise.copy(config = config.copy(targetWeightKg = weightKg)))
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
                    )
                }
        }.take(limit)
        .toList()
