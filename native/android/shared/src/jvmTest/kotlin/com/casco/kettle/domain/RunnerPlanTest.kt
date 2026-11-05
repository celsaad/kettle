package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `resolveRunnerPlan`, from RN `session.test.tsx`'s "before the first step" cases and the screen's
 * resolution in `session.tsx`: which of the five a start is, before any count-in.
 */
class RunnerPlanTest {
    private val bench = Exercise("bench", "Bench", ExerciseConfig.Reps(3, 5, targetWeightKg = 80.0, restSec = 120.0))
    private val main = Workout("main", "Main", listOf(WorkoutBlock.ExerciseBlock("bench")))
    private val empty = Workout("empty", "Empty", listOf(WorkoutBlock.ExerciseBlock("gone")))
    private val program =
        Program(
            "p",
            "P",
            listOf(
                ProgramWeek.Training(1, "Day 1", "main"),
                ProgramWeek.RestDay(1, "Day 2"),
                ProgramWeek.Training(
                    2,
                    workoutId = "main",
                    overrides =
                        listOf(ProgramOverride.ExerciseOverride("bench", mapOf("sets" to OverrideValue.Numeric(5.0)))),
                ),
                ProgramWeek.Training(3, workoutId = "gone"),
            ),
        )
    private val library = Library(1, listOf(bench), listOf(main, empty), listOf(program))

    private fun planned(params: SessionParams) = resolveRunnerPlan(RunnerStart.Planned(params), library)

    private fun slot(
        week: Long,
        day: String? = null,
    ) = planned(SessionParams.ProgramSlot("p", week, day))

    private fun setsIn(plan: RunnerPlan): Int = setStepsForMember((plan as RunnerPlan.Runnable).steps, "0")

    @Test
    fun `runs a workout by id`() {
        val plan = planned(SessionParams.Workout("main"))

        assertEquals(RunnerPlan.Runnable(main, library.exercises, buildSteps(main, library.exercises)), plan)
    }

    @Test
    fun `falls back to the library's first workout when the start names nothing`() {
        assertEquals(planned(SessionParams.Workout("main")), resolveRunnerPlan(null, library))
    }

    @Test
    fun `has nothing to fall back on in an empty library`() {
        assertEquals(RunnerPlan.Missing, resolveRunnerPlan(null, library.copy(workouts = emptyList())))
    }

    @Test
    fun `starts an ad-hoc session with no workout and no steps, whatever the library holds`() {
        assertEquals(
            RunnerPlan.Runnable(null, library.exercises, emptyList()),
            resolveRunnerPlan(RunnerStart.Adhoc, library.copy(workouts = emptyList())),
        )
    }

    @Test
    fun `applies a program week's overrides to the steps and to the exercises the pickers offer`() {
        val plan = slot(2)

        assertEquals(3, setsIn(slot(1, "Day 1")))
        assertEquals(5, setsIn(plan))
        assertEquals(
            ExerciseConfig.Reps(5, 5, targetWeightKg = 80.0, restSec = 120.0),
            (plan as RunnerPlan.Runnable).exercises.single().config,
        )
    }

    @Test
    fun `explains a rest week rather than calling it missing`() {
        assertEquals(RunnerPlan.RestWeek, slot(1, "Day 2"))
    }

    @Test
    fun `is missing for an id that resolves to nothing`() {
        assertEquals(RunnerPlan.Missing, planned(SessionParams.Workout("nope")))
        assertEquals(RunnerPlan.Missing, planned(SessionParams.ProgramSlot("nope", 1, null)))
        assertEquals(RunnerPlan.Missing, slot(9))
        assertEquals(RunnerPlan.Missing, slot(1, "Day 7"))
        // The week is there; its workout isn't.
        assertEquals(RunnerPlan.Missing, slot(3))
    }

    @Test
    fun `refuses to count in a workout with no runnable steps`() {
        assertEquals(RunnerPlan.NoSteps(empty), planned(SessionParams.Workout("empty")))
    }

    @Test
    fun `says a workout is too long before running it, and still offers what was built`() {
        val big = Exercise("big", "Big", ExerciseConfig.Reps(500, 5, restSec = 60.0))
        val long = Workout("long", "Long", List(22) { WorkoutBlock.ExerciseBlock("big") })
        val plan =
            resolveRunnerPlan(
                RunnerStart.Planned(SessionParams.Workout("long")),
                Library(1, listOf(big), listOf(long), emptyList()),
            )

        assertTrue(plan is RunnerPlan.TooLong)
        val runnable = (plan as RunnerPlan.TooLong).plan
        assertEquals(long, runnable.workout)
        assertEquals(buildSteps(long, listOf(big)), runnable.steps)
    }

    @Test
    fun `says nothing on a workout that fits, which is every real one`() {
        assertTrue(planned(SessionParams.Workout("main")) is RunnerPlan.Runnable)
    }
}
