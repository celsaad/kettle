package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Program detail's weeks (#437): where a program stands, and every slot placed against it. */
class ProgramGridTest {
    private val program =
        Program(
            "base",
            "Base",
            listOf(
                ProgramWeek.Training(1, "Mon", "a"),
                ProgramWeek.RestDay(1, "Tue"),
                ProgramWeek.Training(
                    2,
                    "Mon",
                    "a",
                    overrides =
                        listOf(
                            ProgramOverride.ExerciseOverride("x", mapOf("sets" to OverrideValue.Numeric(4.0))),
                        ),
                ),
                ProgramWeek.Training(1, "Wed", "b"),
            ),
        )

    private fun session(
        week: Long,
        day: String,
    ) = Session(1, "s", "a", "base", week, day, "2026-08-01T10:00:00.000Z", "2026-08-01T10:30:00.000Z", emptyList())

    @Test
    fun `stands at the slot after the newest session run under it`() {
        assertEquals(1, programPosition(program, listOf(session(1, "Mon")))?.index)
    }

    @Test
    fun `stands at the start with nothing run, active or not`() {
        assertEquals(0, programPosition(program, emptyList())?.index)
        assertNull(programPosition(program.copy(weeks = emptyList()), emptyList()))
    }

    /** Weeks by number, slots in file order within one; Behind, Current, Ahead by position alone. */
    @Test
    fun `places every slot of every week against the position`() {
        val grid = programGrid(programPosition(program, listOf(session(1, "Tue")))!!)

        assertEquals(listOf(1L, 2L), grid.map { it.week })
        assertEquals(
            listOf(SlotPlace.Behind, SlotPlace.Behind, SlotPlace.Current),
            grid[0].slots.map { it.place },
        )
        assertEquals(listOf(false, true, false), grid[0].slots.map { it.rest })
        assertEquals(listOf(SlotPlace.Ahead), grid[1].slots.map { it.place })
        assertEquals(listOf(true), grid[1].slots.map { it.changed })
    }
}
