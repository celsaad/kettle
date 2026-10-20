package com.casco.kettle.domain

/**
 * A program week by number, as `findProgramWeek` in `src/domain/program.ts`. A program that runs
 * several sessions a week repeats the number, and [day] tells them apart; without one, the first
 * week with the number is the match, which is all a program with no `day` anywhere needs.
 */
fun findProgramWeek(
    program: Program,
    weekNumber: Long,
    day: String? = null,
): ProgramWeek? =
    if (day != null) {
        program.weeks.firstOrNull { it.week == weekNumber && it.day == day }
    } else {
        program.weeks.firstOrNull { it.week == weekNumber }
    }

/** The program's distinct week numbers, ascending, since several days can share one. */
fun programWeekNumbers(program: Program): List<Long> =
    program.weeks
        .map { it.week }
        .distinct()
        .sorted()

/** A week's workout and the library's whole exercise list, each with that week's overrides applied. */
data class ResolvedWeek(
    val workout: Workout,
    val exercises: List<Exercise>,
)

/**
 * What one program week runs, as `resolveWorkoutForWeek` in TS: its workout with this week's block
 * overrides applied to the circuits they name, and every exercise in the library with this week's
 * exercise overrides applied. Anything the week doesn't override is returned as it is.
 *
 * Null for a missing week, a rest week and a workout id that resolves to nothing alike. Callers that
 * need to tell a rest week apart ask [findProgramWeek] instead.
 */
fun resolveWorkoutForWeek(
    program: Program,
    weekNumber: Long,
    library: Library,
    day: String? = null,
): ResolvedWeek? {
    val week = findProgramWeek(program, weekNumber, day) as? ProgramWeek.Training
    val workout = week?.let { library.workouts.firstOrNull { candidate -> candidate.id == it.workoutId } }
    if (week == null || workout == null) return null

    // Associated, so a second override of the same target replaces the first, as TS's `Map` does.
    val overrides = week.overrides.orEmpty()
    val exerciseOverrides =
        overrides.filterIsInstance<ProgramOverride.ExerciseOverride>().associate { it.exerciseId to it.config }
    val blockOverrides =
        overrides.filterIsInstance<ProgramOverride.BlockOverride>().associate {
            it.blockId to it.config
        }

    val exercises =
        library.exercises.map { exercise ->
            exerciseOverrides[exercise.id]?.let { applyExerciseOverride(exercise, it) } ?: exercise
        }
    val blocks =
        workout.blocks.map { block ->
            val config = (block as? WorkoutBlock.Circuit)?.id?.let(blockOverrides::get)
            if (config != null) applyBlockOverride(block, config) else block
        }
    return ResolvedWeek(workout.copy(blocks = blocks), exercises)
}
