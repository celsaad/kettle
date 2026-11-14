package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `workoutShape`, from RN's `workout-shape.test.ts`, which went with RN Home (#235), less its duration
 * cases: the estimate went in #412, since a reps block only knew its rest.
 */
class WorkoutShapeTest {
    private val exercises =
        listOf(
            Exercise("pullups", "Pull-ups", ExerciseConfig.Reps(3, 6, restSec = 60.0)),
            Exercise("burpees", "Burpees", ExerciseConfig.Hiit(40.0, 20.0, 3)),
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

        assertEquals(listOf(ExerciseType.Reps, ExerciseType.Hiit), shape.types)
    }

    // A workout of squats and rests is a reps workout rather than a mixed one.
    @Test
    fun `never counts rest towards the types`() {
        val shape = shapeOf(one("burpees"), one("breather"))

        assertEquals(listOf(ExerciseType.Hiit), shape.types)
    }

    // The library is hand-edited YAML, so a block can name an exercise that is gone. It still counts as
    // a block, with no type.
    @Test
    fun `survives a block naming an exercise that is gone`() {
        val shape = shapeOf(one("ghost"), one("burpees"))

        assertEquals(2, shape.blockCount)
        assertEquals(listOf(ExerciseType.Hiit), shape.types)
    }
}
