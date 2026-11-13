package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** "Try 53.5 lb" (#410): when double progression suggests a load, and how big the step is. */
class ProgressionTest {
    private val squat = RunnerStep.Reps(0, "k", "squat", "Squat", 8, 12, 20.0, 1, 3, null)

    /** A finished session that logged [loads] at [reps] each, the newest given first in a list. */
    private fun session(
        id: String,
        reps: Long,
        vararg loads: Double?,
    ) = finishedSession(id, SessionEntry.Reps("squat", loads.map { RepsSetLog(reps, it, restTakenSec = 90.0) }))

    @Test
    fun `suggests last load plus the default step once every set reached the top`() {
        val prior = listOf(session("last", 12, 20.0, 20.0, 20.0))

        assertEquals(22.5, progressionLoadKg(squat, prior, UnitSystem.METRIC))
        // 5 lb, the imperial jump, stored in kilograms.
        assertEquals(
            20.0 + fromDisplayWeight(5.0, UnitSystem.IMPERIAL),
            progressionLoadKg(squat, prior, UnitSystem.IMPERIAL),
        )
    }

    @Test
    fun `learns the step from the user's own last increase`() {
        // Newest first: 24 after 22 after 20, so the last increase was 2.
        val prior = listOf(session("c", 12, 24.0, 24.0), session("b", 10, 22.0), session("a", 10, 20.0))

        assertEquals(26.0, progressionLoadKg(squat, prior, UnitSystem.METRIC))
    }

    @Test
    fun `suggests nothing when a set fell short, or there's no top to reach`() {
        val sets = listOf(RepsSetLog(12, 20.0, restTakenSec = 90.0), RepsSetLog(11, 20.0, restTakenSec = 90.0))
        val short = finishedSession("short", SessionEntry.Reps("squat", sets))

        assertNull(progressionLoadKg(squat, listOf(short), UnitSystem.METRIC))
        assertNull(
            progressionLoadKg(squat.copy(targetRepsMax = null), listOf(session("top", 12, 20.0)), UnitSystem.METRIC),
        )
    }

    @Test
    fun `suggests nothing for bodyweight, with no history, or unfinished history`() {
        assertNull(progressionLoadKg(squat, listOf(session("bw", 12, null, null)), UnitSystem.METRIC))
        assertNull(progressionLoadKg(squat, emptyList(), UnitSystem.METRIC))
        val unfinished = session("open", 12, 20.0).copy(endedAt = null)
        assertNull(progressionLoadKg(squat, listOf(unfinished), UnitSystem.METRIC))
    }

    @Test
    fun `a program week that sets the load wins over a suggestion`() {
        val prior = listOf(session("last", 12, 20.0, 20.0))

        assertNull(progressionLoadKg(squat, prior, UnitSystem.METRIC, weightSetBy = setOf("squat")))
        assertEquals(22.5, progressionLoadKg(squat, prior, UnitSystem.METRIC, weightSetBy = setOf("bench")))
    }

    @Test
    fun `a program week reports the exercises whose load it sets`() {
        val program =
            Program(
                "p",
                "P",
                listOf(
                    ProgramWeek.Training(
                        week = 1,
                        workoutId = "w",
                        overrides =
                            listOf(
                                ProgramOverride.ExerciseOverride(
                                    "pullups",
                                    mapOf("target_weight" to OverrideValue.Numeric(25.0)),
                                ),
                                ProgramOverride.ExerciseOverride("dips", mapOf("sets" to OverrideValue.Numeric(4.0))),
                            ),
                    ),
                ),
            )
        val library =
            Library(1, RUNNER_EXERCISES, listOf(workoutOf(single("pullups"), single("dips"))), listOf(program))

        assertEquals(setOf("pullups"), resolveWorkoutForWeek(program, 1, library)?.weightSetBy)
        val plan = resolveRunnerPlan(RunnerStart.Planned(SessionParams.ProgramSlot("p", 1, null)), library)
        assertEquals(setOf("pullups"), (plan as RunnerPlan.Runnable).weightSetBy)
    }
}
