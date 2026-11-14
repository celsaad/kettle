package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The program editor's week and day moves (#438), on a draft of three weeks: two days, one, one. */
class ProgramWeekMovesTest {
    private val draft =
        programDraftOf(
            Program(
                "p",
                "P",
                listOf(
                    ProgramWeek.Training(1, "Mon", "a"),
                    ProgramWeek.RestDay(1, "Tue"),
                    ProgramWeek.Training(2, "Mon", "b"),
                    ProgramWeek.Training(3, "Mon", "c"),
                ),
            ),
        )

    /** Each entry as "week:day", in draft (run) order. */
    private fun ProgramDraft.shape() = weeks.map { "${it.week.week}:${it.week.day}" }

    private fun ProgramDraft.workoutsOf(week: Long) = daysOf(week).map { (it.week as? ProgramWeek.Training)?.workoutId }

    @Test
    fun `groups the weeks by number, lowest first, and a week's days in run order`() {
        assertEquals(listOf(1L, 2L, 3L), draft.weekNumbers)
        assertEquals(listOf("Mon", "Tue"), draft.daysOf(1).map { it.week.day })
    }

    @Test
    fun `moves a week by swapping numbers with its neighbour, and stops at the ends`() {
        val moved = draft.movingWeek(2, -1)

        // In week order in the list too, which is the order the file is written in.
        assertEquals(listOf("1:Mon", "2:Mon", "2:Tue", "3:Mon"), moved.shape())
        assertEquals(listOf("b"), moved.workoutsOf(1))
        assertEquals(listOf("a", null), moved.workoutsOf(2))
        assertEquals(draft, draft.movingWeek(1, -1))
        assertEquals(draft, draft.movingWeek(3, +1))
    }

    /** Right after it, the later weeks up one, as agreed on #438. */
    @Test
    fun `duplicates a week right after it, moving the later weeks up one`() {
        val copied = draft.duplicatingWeek(1)

        assertEquals(listOf("1:Mon", "1:Tue", "2:Mon", "2:Tue", "3:Mon", "4:Mon"), copied.shape())
        assertEquals(listOf("b"), copied.workoutsOf(3))
        assertEquals(
            copied.weeks.size,
            copied.weeks
                .map { it.key }
                .distinct()
                .size,
        )
    }

    /** No gap left, as agreed on #438. */
    @Test
    fun `deletes a week and closes the gap`() {
        val deleted = draft.deletingWeek(2)

        assertEquals(listOf("1:Mon", "1:Tue", "2:Mon"), deleted.shape())
        assertEquals(listOf("c"), deleted.workoutsOf(2))
    }

    @Test
    fun `adds a day at the end of its week, labelled, running the first workout`() {
        val added = draft.addingDay(1, "Day 3", listOf(Workout("w", "W", emptyList())))

        assertEquals(listOf("1:Mon", "1:Tue", "1:Day 3", "2:Mon", "3:Mon"), added.shape())
        assertEquals("w", (added.daysOf(1).last().week as ProgramWeek.Training).workoutId)
    }

    /** Day 1–3 with Day 1 removed: the next new day is Day 1 again, never a second Day 3. */
    @Test
    fun `picks the first day label its week doesn't have`() {
        val week =
            programDraftOf(
                Program("p", "P", listOf("Day 2", "Day 3").map { ProgramWeek.Training(1, it, "a") }),
            )

        assertEquals("Day 1", week.freeDayLabel(1) { "Day $it" })
        assertEquals("Day 4", week.addingDay(1, "Day 1", emptyList()).freeDayLabel(1) { "Day $it" })
    }

    /** An unlabelled first day reads "Day 1" on screen, so a new day can't take that name too. */
    @Test
    fun `counts an unlabelled day's shown name as taken`() {
        val unlabelled = programDraftOf(Program("p", "P", listOf(ProgramWeek.Training(1, workoutId = "a"))))

        assertEquals("Day 2", unlabelled.freeDayLabel(1) { "Day $it" })
    }

    @Test
    fun `copies a week whole, its notes, rest days and changes`() {
        val override = ProgramOverride.ExerciseOverride("x", mapOf("sets" to OverrideValue.Numeric(4.0)))
        val full =
            programDraftOf(
                Program(
                    "p",
                    "P",
                    listOf(
                        ProgramWeek.Training(1, "Mon", "a", "Go easy", listOf(override)),
                        ProgramWeek.RestDay(1, "Tue", "Walk"),
                    ),
                ),
            )

        val copy = full.duplicatingWeek(1).daysOf(2).map { it.week }

        assertEquals(
            listOf(
                ProgramWeek.Training(2, "Mon", "a", "Go easy", listOf(override)),
                ProgramWeek.RestDay(2, "Tue", "Walk"),
            ),
            copy,
        )
    }

    @Test
    fun `moves a day within its week only`() {
        val monday = draft.weeks.first().key

        assertEquals(listOf("1:Tue", "1:Mon", "2:Mon", "3:Mon"), draft.movingDay(monday, +1).shape())
        assertEquals(draft, draft.movingDay(monday, -1))
        // Week 2's only day has no neighbour in its week, and week 1's last day isn't one.
        assertEquals(draft, draft.movingDay(draft.daysOf(2).single().key, -1))
    }
}
