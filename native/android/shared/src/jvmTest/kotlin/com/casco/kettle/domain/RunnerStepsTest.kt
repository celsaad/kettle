package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The step model's cases that `conformance/steps.yaml` can't hold, because the schema refuses the
 * input that reaches them: a count past the per-exercise ceiling, a 0-second hold end, zero work.
 * Built from domain values directly, as RN's `build-steps.test.ts` does, which these twin. The Next
 * card's descriptors are here too, since TS renders them to strings and the goldens can't pin those.
 */
class RunnerStepsTest {
    private fun workoutOf(vararg blocks: WorkoutBlock) = Workout("w", "W", blocks.toList())

    private fun one(id: String) = WorkoutBlock.ExerciseBlock(id)

    private fun reps(
        id: String,
        sets: Long,
        restSec: Double = 0.0,
    ) = Exercise(id, id, ExerciseConfig.Reps(sets, 5, restSec = restSec))

    @Test
    fun `leaves a hold with a 0-second end without one, so it can still be run by hand`() {
        val hold = Exercise("h", "H", ExerciseConfig.TimedHold(1, holdSecMin = 0.0, restSec = 0.0))
        val step = buildSteps(workoutOf(one("h")), listOf(hold)).single() as RunnerStep.Hold

        assertNull(step.holdEndSec)
        assertEquals(0.0, step.holdTargetSec)
    }

    @Test
    fun `returns no steps for a workout with no blocks`() {
        assertEquals(emptyList<RunnerStep>(), buildSteps(workoutOf(), emptyList()))
    }

    @Test
    fun `returns no steps when every exercise is configured to zero work`() {
        val exercises =
            listOf(
                reps("r", 0),
                Exercise("t", "T", ExerciseConfig.TimedHold(0, 30.0, restSec = 10.0)),
                Exercise("h", "H", ExerciseConfig.Hiit(30.0, 10.0, 0)),
            )

        assertEquals(emptyList<RunnerStep>(), buildSteps(workoutOf(one("r"), one("t"), one("h")), exercises))
    }

    @Test
    fun `clamps an exercise block rather than allocating what it asks for, and says so`() {
        val built = buildStepsWithLimits(workoutOf(one("many")), listOf(reps("many", 200_000)))

        assertEquals(2000, built.steps.size)
        assertTrue(built.steps.all { it is RunnerStep.Reps })
        assertTrue(built.truncated)
    }

    // The case a length test can't see: 3999 steps, far under the workout ceiling, and still cut short.
    @Test
    fun `flags a per-exercise clamp that leaves the list well under the workout ceiling`() {
        val built = buildStepsWithLimits(workoutOf(one("m")), listOf(reps("m", 3000, restSec = 60.0)))

        assertEquals(3999, built.steps.size)
        assertTrue(built.truncated)
    }

    @Test
    fun `clamps an oversized circuit member visit too`() {
        val many = Exercise("many", "Many", ExerciseConfig.Hiit(30.0, 0.0, 200_000))
        val circuit = WorkoutBlock.Circuit(rounds = 1, members = listOf(CircuitMember("many"), CircuitMember("p")))
        val built = buildStepsWithLimits(workoutOf(circuit), listOf(many, reps("p", 1)))

        assertEquals(2001, built.steps.size)
        assertEquals(CircuitPosition(1, 1, 1, 2), built.steps.first().circuit)
        assertTrue(built.truncated)
    }

    @Test
    fun `flags an emom the per-exercise ceiling really does cut short`() {
        val emom = Exercise("e", "E", ExerciseConfig.Emom(0.5, 1000.0))
        val built = buildStepsWithLimits(workoutOf(one("e")), listOf(emom))

        assertEquals(2000, built.steps.size)
        assertTrue(built.truncated)
    }

    @Test
    fun `swaps to nothing as no swap at all, rather than deleting the member`() {
        val steps = buildSteps(workoutOf(one("p")), listOf(reps("p", 3)))

        assertEquals(steps, swapExerciseForMember(steps, "0", 0, reps("empty", 0), "0:swap-1"))
    }

    @Test
    fun `previews a hold's range, a fixed mark, and no target for a max-effort hold`() {
        fun hold(
            min: Double?,
            max: Double?,
        ) = RunnerStep.Hold(0, "0", "h", "H", min, max, max ?: min, 2, 3, null)

        assertEquals(StepPreview.Hold("H", "h", 2, 3, HoldTarget(15.0, 25.0)), previewFor(hold(15.0, 25.0)))
        assertEquals(HoldTarget(15.0, null), holdTarget(hold(15.0, null)))
        assertNull(holdTarget(hold(null, null)))
        // TS tests the maximum for truthiness, so a 0 one reads as a fixed target.
        assertEquals(HoldTarget(15.0, null), holdTarget(hold(15.0, 0.0)))
    }

    @Test
    fun `previews a reps target as a range or a single number`() {
        fun reps(max: Long?) = RunnerStep.Reps(0, "0", "p", "P", 8, max, null, 1, 3, null)

        assertEquals(StepPreview.Reps("P", "p", 1, 3, RepsTarget(8, 12)), previewFor(reps(12)))
        assertEquals(RepsTarget(8, null), repsTarget(reps(null)))
        assertEquals(RepsTarget(8, null), repsTarget(reps(0)))
    }

    @Test
    fun `previews an interval by its round when there are several, and by its length when there is one`() {
        val exercises =
            listOf(
                Exercise("b", "Burpees", ExerciseConfig.Hiit(40.0, 0.0, 4)),
                Exercise("g", "Grinder", ExerciseConfig.Amrap(600.0)),
            )
        val steps = buildSteps(workoutOf(one("b"), one("g")), exercises)

        assertEquals(
            StepPreview.Interval("Burpees", "b", IntervalVariant.HIIT, IntervalProgress.Round(2, 4)),
            upcomingPreview(steps, 0),
        )
        assertEquals(
            StepPreview.Interval("Grinder", "g", IntervalVariant.AMRAP, IntervalProgress.Seconds(600.0)),
            upcomingPreview(steps, 3),
        )
    }

    @Test
    fun `previews nothing for a rest, or past the end`() {
        val rest = RunnerStep.Rest(0, "0", "r", standalone = true, seconds = 60.0)

        assertNull(previewFor(rest))
        assertNull(upcomingPreview(listOf(rest), 0))
        assertFalse(restFollows(listOf(rest), 0))
    }
}
