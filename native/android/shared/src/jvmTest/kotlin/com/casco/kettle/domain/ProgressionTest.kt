package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** "Try 53.5 lb" (#410): when double progression suggests a load, and how big the step is. */
class ProgressionTest {
    /** Two sets planned, 8 to 12 reps. */
    private val squat = RunnerStep.Reps(0, "k", "squat", "Squat", 8, 12, 20.0, 1, 2, null)

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
            progressionLoadKg(
                squat.copy(targetRepsMax = null),
                listOf(session("top", 12, 20.0, 20.0)),
                UnitSystem.METRIC,
            ),
        )
    }

    @Test
    fun `learns from the last new high, not a return from a deload or a run at one load`() {
        // Newest first: back to 20 after a deload to 15 is no new high; 20 over 17.5 was the last one.
        val deload =
            listOf(
                session("d", 12, 20.0, 20.0),
                session("c", 12, 15.0, 15.0),
                session("b", 12, 20.0),
                session("a", 12, 17.5),
            )
        assertEquals(22.5, progressionLoadKg(squat, deload, UnitSystem.METRIC))
        assertEquals(2.5, learnedStepKg(listOf(20.0, 15.0, 20.0, 17.5)))
        // Weeks at the same load before a jump of 5: the jump, not the runs.
        assertEquals(5.0, learnedStepKg(listOf(25.0, 25.0, 20.0, 20.0)))
        assertNull(learnedStepKg(listOf(20.0, 20.0)))
    }

    @Test
    fun `the base is last session's heaviest set, the load the step is learned from`() {
        // A back-off set last: the work was at 20, not the 15 it ended on.
        val prior = listOf(session("last", 12, 20.0, 15.0))
        assertEquals(22.5, progressionLoadKg(squat, prior, UnitSystem.METRIC))
    }

    @Test
    fun `a session that logged fewer sets than planned suggests nothing`() {
        // One of two sets, at the top: it didn't reach the top on every set the step plans.
        assertNull(progressionLoadKg(squat, listOf(session("short", 12, 20.0)), UnitSystem.METRIC))
    }

    @Test
    fun `Try is offered only above the load on screen as the user reads it`() {
        val suggested = 20.0 + fromDisplayWeight(5.0, UnitSystem.IMPERIAL) // 49.1 lb
        val typed = fromDisplayWeight(49.1, UnitSystem.IMPERIAL) // the same 49.1 lb from the pad
        assertNull(offeredLoadKg(suggested, typed, UnitSystem.IMPERIAL))
        assertEquals(suggested, offeredLoadKg(suggested, 20.0, UnitSystem.IMPERIAL))
        assertNull(offeredLoadKg(null, 0.0, UnitSystem.METRIC))
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

        assertNull(progressionLoadKg(squat, prior, UnitSystem.METRIC, loadsSetByProgram = setOf("squat")))
        assertEquals(22.5, progressionLoadKg(squat, prior, UnitSystem.METRIC, loadsSetByProgram = setOf("bench")))
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

        assertEquals(setOf("pullups"), resolveWorkoutForWeek(program, 1, library)?.loadsSetByProgram)
        val plan = resolveRunnerPlan(RunnerStart.Planned(SessionParams.ProgramSlot("p", 1, null)), library)
        assertEquals(setOf("pullups"), (plan as RunnerPlan.Runnable).loadsSetByProgram)
    }
}
