package com.casco.kettle.domain

// What the workout editor (#242) makes of its draft on Save, and what it does to the library: the
// library store's `saveWorkout` and `deleteWorkout`, and the delete guard RN ran inline.

/** Why Save refused. */
sealed interface WorkoutProblem {
    data object NameRequired : WorkoutProblem

    /** A name with no letter or digit in it, which leaves nothing to make an id from. */
    data object NoId : WorkoutProblem
}

sealed interface WorkoutSubmit {
    data class Built(
        val workout: Workout,
    ) : WorkoutSubmit

    data class Refused(
        val problem: WorkoutProblem,
    ) : WorkoutSubmit
}

/**
 * What Save makes of [draft]. [existing] is the workout being edited, null for a new one: it keeps its
 * id, so a rename can't orphan the program weeks that point at it. A new workout's id is its name's
 * [slugify].
 */
fun submitWorkout(
    draft: WorkoutDraft,
    existing: Workout?,
): WorkoutSubmit {
    val id = existing?.id ?: slugify(draft.name)
    return when {
        draft.name.isBlank() -> WorkoutSubmit.Refused(WorkoutProblem.NameRequired)
        id.isEmpty() -> WorkoutSubmit.Refused(WorkoutProblem.NoId)
        else -> WorkoutSubmit.Built(Workout(id, draft.name.trim(), draft.blocks.map { it.block }))
    }
}

/** The library with [workout] in it: in place of the one with its id, or else at the end. */
fun Library.withWorkout(workout: Workout): Library = copy(workouts = workouts.upserting(workout, Workout::id))

/** The library without workout [id]. Nothing checks it's unused: that's [programsUsing]'s, first. */
fun Library.withoutWorkout(id: String): Library = copy(workouts = workouts.filterNot { it.id == id })

/** The programs with a training week pointing at workout [id], which deleting it would leave dangling. */
fun programsUsing(
    library: Library,
    id: String,
): List<Program> =
    library.programs.filter { program ->
        program.weeks.any { it is ProgramWeek.Training && it.workoutId == id }
    }
