package com.casco.kettle.domain

/**
 * What an import added and replaced, by id, as `MergeSummary` in `src/domain/merge.ts`. Each list is
 * in the order the incoming file had it.
 */
data class MergeSummary(
    val newExercises: List<String>,
    val updatedExercises: List<String>,
    val newWorkouts: List<String>,
    val updatedWorkouts: List<String>,
    val newPrograms: List<String>,
    val updatedPrograms: List<String>,
)

/**
 * Why a merge was refused, as data rather than a sentence: the import screen renders it. Every field
 * is a user-authored id or day label, so it is shown verbatim, never translated.
 */
sealed interface MergeError {
    data class UnknownExercise(
        val workoutId: String,
        val exerciseId: String,
    ) : MergeError

    data class UnknownWorkout(
        val programId: String,
        val week: Long,
        val day: String?,
        val workoutId: String,
    ) : MergeError
}

sealed interface MergeResult {
    data class Ok(
        val library: Library,
        val summary: MergeSummary,
    ) : MergeResult

    data class Refused(
        val error: MergeError,
    ) : MergeResult
}

/**
 * An import, as `mergeLibraries` in `src/domain/merge.ts`: an incoming item replaces the existing one
 * with its id whole, never field by field, or is appended if it's new. Existing items keep their
 * place, which a [LinkedHashMap] does on replace as a JS `Map` does. The version is the existing
 * file's.
 *
 * The merged whole is then checked for dangling references, since an import can satisfy a reference
 * with an exercise the user already has, or break one by replacing the workout that held it. Ids
 * can't collide by construction, and every type and config was checked when each side was parsed.
 */
fun mergeLibraries(
    existing: Library,
    incoming: Library,
): MergeResult {
    val exercises = mergeById(existing.exercises, incoming.exercises) { it.id }
    val workouts = mergeById(existing.workouts, incoming.workouts) { it.id }
    val programs = mergeById(existing.programs, incoming.programs) { it.id }
    val library = Library(existing.version, exercises.merged, workouts.merged, programs.merged)

    return danglingReference(library)?.let(MergeResult::Refused) ?: MergeResult.Ok(
        library,
        MergeSummary(
            newExercises = exercises.added,
            updatedExercises = exercises.updated,
            newWorkouts = workouts.added,
            updatedWorkouts = workouts.updated,
            newPrograms = programs.added,
            updatedPrograms = programs.updated,
        ),
    )
}

private class MergedById<T>(
    val merged: List<T>,
    val added: List<String>,
    val updated: List<String>,
)

private fun <T> mergeById(
    existing: List<T>,
    incoming: List<T>,
    id: (T) -> String,
): MergedById<T> {
    val added = mutableListOf<String>()
    val updated = mutableListOf<String>()
    val byId = existing.associateByTo(LinkedHashMap(), id)
    for (item in incoming) {
        if (id(item) in byId) updated += id(item) else added += id(item)
        byId[id(item)] = item
    }
    return MergedById(byId.values.toList(), added, updated)
}

/** The first reference the merged library can't resolve, in file order, or null when there is none. */
private fun danglingReference(library: Library): MergeError? = unknownExercise(library) ?: unknownWorkout(library)

private fun unknownExercise(library: Library): MergeError? {
    val exerciseIds = library.exercises.mapTo(HashSet()) { it.id }
    return library.workouts.firstNotNullOfOrNull { workout ->
        val missing = workout.blocks.flatMap(::blockExerciseIds).firstOrNull { it !in exerciseIds }
        missing?.let { MergeError.UnknownExercise(workout.id, it) }
    }
}

private fun unknownWorkout(library: Library): MergeError? {
    val workoutIds = library.workouts.mapTo(HashSet()) { it.id }
    return library.programs.firstNotNullOfOrNull { program ->
        // A rest week names no workout, so only training weeks can dangle.
        val missing = program.weeks.filterIsInstance<ProgramWeek.Training>().firstOrNull { it.workoutId !in workoutIds }
        missing?.let { MergeError.UnknownWorkout(program.id, it.week, it.day, it.workoutId) }
    }
}

private fun blockExerciseIds(block: WorkoutBlock): List<String> =
    when (block) {
        is WorkoutBlock.ExerciseBlock -> listOf(block.exerciseId)
        is WorkoutBlock.Circuit -> block.members.map { it.exerciseId }
    }
