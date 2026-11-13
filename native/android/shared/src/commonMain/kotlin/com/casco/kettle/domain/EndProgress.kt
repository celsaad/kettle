package com.casco.kettle.domain

/** What one exercise's progress is counted in, on End early's sheet (#417). */
enum class EndUnit {
    SETS,
    ROUNDS,
    MINUTES,
    INTERVALS,

    /** One go, an AMRAP's or a cardio's outside a circuit: done, or not started. */
    ONCE,
}

/** One exercise on End early's sheet: [done] of its [total] steps, in its own [unit]. */
data class EndRow(
    val name: String,
    val done: Int,
    val total: Int,
    val unit: EndUnit,
)

/**
 * How far each exercise got, for End early's sheet (#417): its work steps behind the one on screen, of
 * all its steps, as the step list stands after any set added, dropped or swapped. A swap starts a member
 * of its own, so the exercise swapped out and the one swapped in are a row each. Rests aren't counted.
 *
 * In each kind's own unit, since a workout can mix them and no one total covers it: a reps or hold
 * exercise counts sets; a HIIT its rounds; an EMOM its minutes, or its intervals when they aren't
 * minutes; an AMRAP or a cardio is one go. Inside a circuit every member counts the circuit's rounds,
 * a round done once all of that visit's steps are behind the one on screen.
 */
fun endProgress(snapshot: RunnerSnapshot): List<EndRow> =
    snapshot.steps
        .withIndex()
        .filter { it.value is WorkStep }
        .groupBy { it.value.memberKey }
        .values
        .map { visits ->
            val first = visits.first().value as WorkStep
            // Inside a circuit, its rounds: a HIIT member runs all its own rounds on every visit, and
            // counting those would read "4 of 9 rounds" for a 3-round HIIT in a 3-round circuit.
            val units =
                if (first.circuit !=
                    null
                ) {
                    visits.groupBy { it.value.circuit?.round }.values
                } else {
                    visits.map(::listOf)
                }
            EndRow(
                name = first.exerciseName,
                done = units.count { unit -> unit.all { it.index < snapshot.stepIndex } },
                total = units.size,
                unit = if (first.circuit != null) EndUnit.ROUNDS else progressUnit(first),
            )
        }

private fun progressUnit(step: WorkStep): EndUnit =
    when (step) {
        is RunnerStep.Interval -> {
            when (step.variant) {
                IntervalVariant.HIIT -> EndUnit.ROUNDS
                IntervalVariant.EMOM -> if (step.isMinute) EndUnit.MINUTES else EndUnit.INTERVALS
                IntervalVariant.AMRAP, IntervalVariant.CARDIO -> EndUnit.ONCE
            }
        }

        else -> {
            EndUnit.SETS
        }
    }
