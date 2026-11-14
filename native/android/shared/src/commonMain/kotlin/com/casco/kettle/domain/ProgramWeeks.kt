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

/**
 * How many sessions each of the program's weeks trains (#413), when every week trains the same number;
 * null when they differ or none trains, so Library's row never states a count only some weeks keep.
 * Rest days aren't sessions.
 */
fun programSessionsPerWeek(program: Program): Int? =
    program.weeks
        .groupBy { it.week }
        .values
        .map { days -> days.count { it is ProgramWeek.Training } }
        .distinct()
        .singleOrNull()
        ?.takeIf { it > 0 }

/** The workouts the program trains, for Library's "In your program" (#413). */
fun programWorkoutIds(program: Program): Set<String> =
    program.weeks.mapNotNullTo(mutableSetOf()) { (it as? ProgramWeek.Training)?.workoutId }

/**
 * A week's workout and the library's whole exercise list, each with that week's overrides applied, and
 * [loadsSetByProgram], the exercises whose load those overrides set (`target_weight`).
 */
data class ResolvedWeek(
    val workout: Workout,
    val exercises: List<Exercise>,
    val loadsSetByProgram: Set<String> = emptySet(),
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
    val loadsSetByProgram = exerciseOverrides.filterValues { TARGET_WEIGHT_KEY in it }.keys
    return ResolvedWeek(workout.copy(blocks = blocks), exercises, loadsSetByProgram)
}

/** The three fields both kinds of week have, changed whichever kind this is. */
internal fun ProgramWeek.withShared(
    week: Long = this.week,
    day: String? = this.day,
    notes: String? = this.notes,
): ProgramWeek =
    when (this) {
        is ProgramWeek.Training -> copy(week = week, day = day, notes = notes)
        is ProgramWeek.RestDay -> copy(week = week, day = day, notes = notes)
    }

/** The raw key a week sets an exercise's load with: overrides are snake_case patches. */
private const val TARGET_WEIGHT_KEY = "target_weight"
