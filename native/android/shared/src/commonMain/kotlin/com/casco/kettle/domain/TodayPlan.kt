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
 * The hero card's summary of [workout], from the steps the runner would build, so a program week's
 * overrides reach it exactly as they reach the session: [workout] and [exercises] as the card holds
 * them, overrides applied. One entry per exercise as written, so a circuit member counts once however
 * many rounds it runs, and the same exercise in two blocks counts twice, as the workout lists it.
 */
fun planSummary(
    workout: Workout,
    exercises: List<Exercise>,
): PlanSummary {
    val work = buildStepsWithLimits(workout, exercises).steps.filterIsInstance<WorkStep>()
    val planned =
        work.groupBy { it.memberKey }.values.map { steps ->
            val first = steps.first()
            PlannedExercise(
                first.exerciseName,
                (first as? RunnerStep.Reps)?.targetWeightKg?.takeIf { it.isConfigured() },
            )
        }
    val allSets = work.isNotEmpty() && work.all { it is RunnerStep.Reps || it is RunnerStep.Hold }
    return PlanSummary(planned, work.size.takeIf { allSets })
}
