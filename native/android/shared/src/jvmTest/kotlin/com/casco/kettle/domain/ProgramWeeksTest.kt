package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The counts Library's program rows read (#413): sessions a week, and the workouts a program trains.
 * The words are `ProgramsScreenTest`'s.
 */
class ProgramWeeksTest {
    private fun program(vararg weeks: ProgramWeek) = Program("block", "Block", weeks.toList())

    @Test
    fun `sessions a week counts the training days each week holds, rest days not among them`() {
        val program =
            program(
                ProgramWeek.Training(1, "Day 1", "push-day"),
                ProgramWeek.RestDay(1, "Day 2"),
                ProgramWeek.Training(1, "Day 3", "pull-day"),
                ProgramWeek.Training(2, "Day 1", "push-day"),
                ProgramWeek.Training(2, "Day 3", "pull-day"),
            )

        assertEquals(2, programSessionsPerWeek(program))
    }

    /** A count only some weeks keep would be wrong about the others, so the row says none. */
    @Test
    fun `sessions a week is null when the weeks differ`() {
        val program =
            program(
                ProgramWeek.Training(1, "Day 1", "push-day"),
                ProgramWeek.Training(2, "Day 1", "push-day"),
                ProgramWeek.Training(2, "Day 2", "pull-day"),
            )

        assertEquals(null, programSessionsPerWeek(program))
    }

    @Test
    fun `sessions a week is null for a program of rest alone, or of nothing`() {
        assertEquals(null, programSessionsPerWeek(program(ProgramWeek.RestDay(1, "Day 1"))))
        assertEquals(null, programSessionsPerWeek(program()))
    }

    @Test
    fun `a program's workouts are those its training days name, once each`() {
        val program =
            program(
                ProgramWeek.Training(1, "Day 1", "push-day"),
                ProgramWeek.RestDay(1, "Day 2"),
                ProgramWeek.Training(2, "Day 1", "push-day"),
                ProgramWeek.Training(2, "Day 3", "pull-day"),
            )

        assertEquals(setOf("push-day", "pull-day"), programWorkoutIds(program))
    }
}
