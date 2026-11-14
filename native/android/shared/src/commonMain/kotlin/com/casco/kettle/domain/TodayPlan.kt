package com.casco.kettle.domain

/**
 * Where the next-up card's slot sits in its program (#412), which Today's program row and week strip
 * read. [index] is into the program's rotation: its weeks by number, and within one in file order.
 */
data class ProgramPosition(
    val program: Program,
    val index: Int,
) {
    val weekNumber: Long get() = rotation(program)[index].week

    /** "of 4": the highest week number, since the author numbers the weeks and the last says how long it runs. */
    val weekCount: Long get() = program.weeks.maxOf { it.week }
}

/** Where a strip slot stands against the card's: [Current] is the slot the card shows. */
enum class SlotPlace { Behind, Current, Ahead }

/** One slot of the week strip. [number] counts from 1 within the week; [day] is the user's own text. */
data class StripSlot(
    val number: Int,
    val day: String?,
    val rest: Boolean,
    val place: SlotPlace,
)

/**
 * The slots of [position]'s week, in file order, for Today's strip. Position, not completion, as
 * decided on #408: a program is a rotation, not a calendar, so every slot before the card's is
 * [SlotPlace.Behind] whether or not it was run, which stays true after a wrap or a session started out
 * of turn. A week's slots are contiguous in the rotation, which sorts by week number.
 */
fun weekStrip(position: ProgramPosition): List<StripSlot> {
    val weeks = rotation(position.program)
    val week = weeks[position.index].week
    return weeks
        .withIndex()
        .filter { it.value.week == week }
        .mapIndexed { n, (index, slot) ->
            val place =
                when {
                    index < position.index -> SlotPlace.Behind
                    index == position.index -> SlotPlace.Current
                    else -> SlotPlace.Ahead
                }
            StripSlot(n + 1, slot.day, slot is ProgramWeek.RestDay, place)
        }
}

/** One exercise on the hero card: its name, the user's, and its load in kilograms when it has one. */
data class PlannedExercise(
    val name: String,
    val loadKg: Double?,
)

/**
 * What the hero card says a workout holds. [sets] is null unless every piece of work is a set, a reps
 * or a hold one: an interval, a minute or a round is no set, and counting one as such would be false.
 */
data class PlanSummary(
    val exercises: List<PlannedExercise>,
    val sets: Int?,
)

/**
 * The hero card's summary of [workout], with [workout] and [exercises] as the card holds them, a program
 * week's overrides applied, so the week's load is the one shown. One entry per exercise as written: a
 * circuit member once however many rounds it runs, the same exercise in two blocks twice, a rest block
 * and an exercise that is gone not at all.
 *
 * The exercises come from the blocks rather than the runner's steps, which a workout past
 * [MAX_STEPS_PER_WORKOUT] cuts short. The sets can only come from the steps, so a cut-short workout
 * counts none rather than too few.
 */
fun planSummary(
    workout: Workout,
    exercises: List<Exercise>,
): PlanSummary {
    val byId = exercises.associateBy { it.id }
    val planned =
        workout.blocks
            .flatMap { block ->
                when (block) {
                    is WorkoutBlock.ExerciseBlock -> listOf(block.exerciseId)
                    is WorkoutBlock.Circuit -> block.members.map { it.exerciseId }
                }
            }.mapNotNull { byId[it]?.takeIf { exercise -> exercise.config !is ExerciseConfig.Rest } }
            .map {
                PlannedExercise(
                    it.name,
                    (it.config as? ExerciseConfig.Reps)?.targetWeightKg?.takeIf { kg ->
                        kg.isConfigured()
                    },
                )
            }
    val built = buildStepsWithLimits(workout, exercises)
    val work = built.steps.filterIsInstance<WorkStep>()
    val allSets = !built.truncated && work.isNotEmpty() && work.all { it is RunnerStep.Reps || it is RunnerStep.Hold }
    return PlanSummary(planned, work.size.takeIf { allSets })
}
