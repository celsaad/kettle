package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * `programWeeksWithNotes`, the count that sat inside RN's `(tabs)/programs.tsx` until Programs went
 * native (#232). Its one jest case was the screen's; it is here as the selector's, and the words are
 * `ProgramsScreenTest`'s.
 */
class ProgramWeeksTest {
    private val sets = mapOf("sets" to OverrideValue.Numeric(4.0))

    private fun program(vararg weeks: ProgramWeek) = Program("block", "Block", weeks.toList())

    // `program.weeks` holds one entry per week *and day*, so counting entries over-reported: a
    // four-week program with notes on two days of every week claimed 8 next to a "Weeks 1–4" range.
    @Test
    fun `counts distinct weeks, not the day entries inside them`() {
        val everyWeekTwice =
            (1L..4L).flatMap { week ->
                listOf(
                    ProgramWeek.Training(week, "Day 1", "push-day", notes = "Push a little harder."),
                    ProgramWeek.Training(week, "Day 3", "push-day", notes = "And again."),
                )
            }

        assertEquals(4, programWeeksWithNotes(program(*everyWeekTwice.toTypedArray())))
    }

    @Test
    fun `counts a week with overrides and no notes`() {
        val overridden = ProgramOverride.ExerciseOverride("pushups", sets)

        assertEquals(
            1,
            programWeeksWithNotes(program(ProgramWeek.Training(2, workoutId = "w", overrides = listOf(overridden)))),
        )
    }

    @Test
    fun `counts a rest day's notes`() {
        assertEquals(1, programWeeksWithNotes(program(ProgramWeek.RestDay(3, notes = "Walk instead."))))
    }

    // RN tested `week.notes` and `overrides.length > 0` for truth, so empty ones say nothing.
    @Test
    fun `leaves out a week with empty notes or an empty override list`() {
        val plain =
            program(
                ProgramWeek.Training(1, workoutId = "w"),
                ProgramWeek.Training(2, workoutId = "w", notes = ""),
                ProgramWeek.Training(3, workoutId = "w", overrides = emptyList()),
            )

        assertEquals(0, programWeeksWithNotes(plain))
    }
}
