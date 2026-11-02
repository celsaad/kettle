package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `workoutShape`, case for case from RN's `workout-shape.test.ts`, which went with RN Home (#235). It
 * is the next-up card's only description of what you are about to run, and "~13 min" is the part you
 * decide on. Durations are estimates by construction, since a reps block only knows its rest, so these
 * pin the arithmetic that exists rather than claiming accuracy.
 */
class WorkoutShapeTest {
    private val exercises =
        listOf(
            Exercise("pullups", "Pull-ups", ExerciseConfig.Reps(3, 6, restSec = 60.0)),
            Exercise("burpees", "Burpees", ExerciseConfig.Hiit(40.0, 20.0, 3)),
            Exercise("plank", "Plank", ExerciseConfig.TimedHold(2, 30.0, 60.0, 30.0)),
            Exercise("breather", "Pausa", ExerciseConfig.Rest(90.0)),
        )

    private fun shapeOf(vararg blocks: WorkoutBlock) = workoutShape(Workout("w", "W", blocks.toList()), exercises)

    private fun one(id: String) = WorkoutBlock.ExerciseBlock(id)

    @Test
    fun `counts blocks rather than exercises, so a circuit counts once`() {
        val shape =
            shapeOf(
                one("pullups"),
                WorkoutBlock.Circuit(rounds = 2, members = listOf(CircuitMember("pullups"), CircuitMember("burpees"))),
            )

        assertEquals(2, shape.blockCount)
    }

    // The types are what the summary calls "mixed reps + hiit", so duplicates would read as a longer
    // list of the same thing.
    @Test
    fun `lists each type once, however many blocks use it`() {
        val shape = shapeOf(one("pullups"), one("pullups"), one("burpees"))

        assertEquals(listOf(ExerciseConfig.Reps::class, ExerciseConfig.Hiit::class), shape.types)
    }

    // Rest takes real time, so it counts towards the estimate, but a workout of squats and rests is a
    // reps workout rather than a mixed one. Both halves, since they pull in opposite directions.
    @Test
    fun `counts rest towards the clock but never towards the types`() {
        // 3 × (40 + 20) = 180s, then 90s: 270s, rounded.
        val shape = shapeOf(one("burpees"), one("breather"))

        assertEquals(listOf(ExerciseConfig.Hiit::class), shape.types)
        assertEquals(5, shape.estimatedMinutes)
    }

    @Test
    fun `takes a block-level rest override over the exercise's own duration`() {
        val shape = shapeOf(WorkoutBlock.ExerciseBlock("breather", BlockConfigOverride(durationSec = 300.0)))

        assertEquals(5, shape.estimatedMinutes)
    }

    // A hold ends itself at the top of its range, so that is the honest number to estimate from.
    @Test
    fun `estimates a hold from the top of its range`() {
        // 2 × 60s hold + 1 × 30s rest = 150s.
        assertEquals(3, shapeOf(one("plank")).estimatedMinutes)
    }

    @Test
    fun `multiplies a circuit by its rounds and counts the rest between them`() {
        val shape =
            shapeOf(
                WorkoutBlock.Circuit(
                    rounds = 3,
                    restBetweenExercisesSec = 15.0,
                    restBetweenRoundsSec = 60.0,
                    members = listOf(CircuitMember("plank"), CircuitMember("plank")),
                ),
            )

        // Per round: two 60s visits + one 15s gap = 135s. Three rounds = 405s, plus two 60s round rests.
        assertEquals(9, shape.estimatedMinutes)
    }

    // The library is hand-edited YAML, so a block can name an exercise that is gone. It still counts as
    // a block, with no time and no type.
    @Test
    fun `survives a block naming an exercise that is gone`() {
        val shape = shapeOf(one("ghost"), one("burpees"))

        assertEquals(2, shape.blockCount)
        assertEquals(listOf(ExerciseConfig.Hiit::class), shape.types)
        assertEquals(3, shape.estimatedMinutes)
    }

    // A zero would read as a broken estimate rather than as a short session.
    @Test
    fun `never estimates zero, however little it can account for`() {
        assertEquals(1, shapeOf(one("ghost")).estimatedMinutes)
    }
}
