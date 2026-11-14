package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Workout preview's rest between sets (#439), read off the steps the runner builds. */
class StepRestsTest {
    private fun rests(
        blocks: List<WorkoutBlock>,
        vararg exercises: Exercise,
    ) = restsBetweenSets(buildSteps(Workout("w", "W", blocks), exercises.toList()))

    private fun block(id: String) = WorkoutBlock.ExerciseBlock(id)

    @Test
    fun `a reps exercise rests between its sets`() {
        val squat = Exercise("squat", "Squat", ExerciseConfig.Reps(3, 8, restSec = 90.0))

        assertEquals(mapOf("0" to 90.0), rests(listOf(block("squat")), squat))
    }

    @Test
    fun `a HIIT's rest between rounds counts`() {
        val climbers = Exercise("mc", "Mountain Climbers", ExerciseConfig.Hiit(30.0, 10.0, 4))

        assertEquals(mapOf("0" to 10.0), rests(listOf(block("mc")), climbers))
    }

    @Test
    fun `one set, or no rest, has none`() {
        val once = Exercise("once", "Once", ExerciseConfig.Reps(1, 8, restSec = 90.0))
        val noRest = Exercise("none", "None", ExerciseConfig.Reps(3, 8, restSec = 0.0))

        assertEquals(emptyMap<String, Double>(), rests(listOf(block("once"), block("none")), once, noRest))
    }

    // The block's own entry, keyed by its block: a rest the exercise before it doesn't take.
    @Test
    fun `a Rest block after an exercise isn't the exercise's rest`() {
        val squat = Exercise("squat", "Squat", ExerciseConfig.Reps(1, 8, restSec = 90.0))
        val breather = Exercise("rest", "Rest", ExerciseConfig.Rest(120.0))

        assertEquals(emptyMap<String, Double>(), rests(listOf(block("squat"), block("rest")), squat, breather))
    }

    @Test
    fun `a circuit's rests land on no member`() {
        val squat = Exercise("squat", "Squat", ExerciseConfig.Reps(3, 8, restSec = 90.0))
        val press = Exercise("press", "Press", ExerciseConfig.Reps(3, 8, restSec = 90.0))
        val circuit =
            WorkoutBlock.Circuit(
                rounds = 3,
                restBetweenExercisesSec = 30.0,
                restBetweenRoundsSec = 60.0,
                members = listOf(CircuitMember("squat"), CircuitMember("press")),
            )

        assertEquals(emptyMap<String, Double>(), rests(listOf(circuit), squat, press))
    }

    @Test
    fun `each exercise keeps its own rest`() {
        val squat = Exercise("squat", "Squat", ExerciseConfig.Reps(3, 8, restSec = 90.0))
        val plank = Exercise("plank", "Plank", ExerciseConfig.TimedHold(3, 20.0, 45.0, restSec = 45.0))

        assertEquals(mapOf("0" to 90.0, "1" to 45.0), rests(listOf(block("squat"), block("plank")), squat, plank))
    }
}
