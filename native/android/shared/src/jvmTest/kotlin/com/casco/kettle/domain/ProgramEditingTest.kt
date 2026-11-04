package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * What the program editor (#243) does to its draft and to the library: the state `program-editor.tsx`
 * kept inline, and the store's `saveProgram`/`deleteProgram`. The screen's own cases are
 * `ProgramEditorScreenTest`'s; these are the rules under them.
 *
 * A week is addressed by `week + day`, which is what `resolveWorkoutForWeek` resolves and what a session
 * records as its place, so anything Save lets through with a collision writes a week nothing can reach.
 */
class ProgramEditingTest {
    private val pushDay = Workout("push-day", "Push day", emptyList())
    private val pullDay = Workout("pull-day", "Pull day", emptyList())
    private val workouts = listOf(pushDay, pullDay)

    private fun named(
        name: String,
        weeks: Int,
    ) = (1..weeks).fold(ProgramDraft(name = name)) { draft, _ -> draft.addingWeek(workouts) }

    private fun ProgramDraft.keyAt(index: Int) = weeks[index].key

    private fun built(submit: ProgramSubmit) = (submit as ProgramSubmit.Built).program

    @Nested
    inner class Submitting {
        @Test
        fun `refuses a blank name`() {
            assertEquals(
                ProgramSubmit.Refused(ProgramProblem.NameRequired),
                submitProgram(named("   ", 1), null),
            )
        }

        // Dead downstream: the next week indexes into the sorted weeks, and Home has nothing to offer.
        @Test
        fun `refuses a program with no weeks`() {
            assertEquals(ProgramSubmit.Refused(ProgramProblem.NoWeeks), submitProgram(named("Base", 0), null))
        }

        @Test
        fun `refuses a training week with no workout`() {
            assertEquals(
                ProgramSubmit.Refused(ProgramProblem.WeekNeedsWorkout),
                submitProgram(ProgramDraft(name = "Base").addingWeek(emptyList()), null),
            )
        }

        @Test
        fun `refuses the same week twice when neither has a day`() {
            val draft = named("Base", 2).let { it.steppingWeek(it.keyAt(1), -1) }
            assertEquals(
                ProgramSubmit.Refused(ProgramProblem.DuplicateWeek(1, null)),
                submitProgram(draft, null),
            )
        }

        // The point of `day`: two sessions in one week are two entries, not a duplicate.
        @Test
        fun `allows the same week number when the days differ`() {
            val draft =
                named("Base", 2).let {
                    it.steppingWeek(it.keyAt(1), -1).withDay(it.keyAt(0), "Monday").withDay(it.keyAt(1), "Thursday")
                }
            assertEquals(
                listOf(
                    ProgramWeek.Training(1, "Monday", "push-day"),
                    ProgramWeek.Training(1, "Thursday", "push-day"),
                ),
                built(submitProgram(draft, null)).weeks,
            )
        }

        // Trimmed on Save, not per keystroke as RN did, which ate the space in "Day 1" as it was typed.
        @Test
        fun `trims a day and notes, and stores blank ones as absent`() {
            val draft = named("Base", 1).let { it.withDay(it.keyAt(0), " Day 1 ").withNotes(it.keyAt(0), "   ") }
            assertEquals(listOf(ProgramWeek.Training(1, "Day 1", "push-day")), built(submitProgram(draft, null)).weeks)
        }

        // A day that differs only by its spaces is the same day once Save has trimmed it.
        @Test
        fun `compares days as Save writes them`() {
            val draft =
                named("Base", 2).let {
                    it.steppingWeek(it.keyAt(1), -1).withDay(it.keyAt(0), "Mon").withDay(it.keyAt(1), "Mon ")
                }
            assertEquals(
                ProgramSubmit.Refused(ProgramProblem.DuplicateWeek(1, "Mon")),
                submitProgram(draft, null),
            )
        }

        @Test
        fun `refuses a name that yields no id`() {
            assertEquals(ProgramSubmit.Refused(ProgramProblem.NoId), submitProgram(named("🏋️", 1), null))
        }

        // Sessions record `programId`, so re-deriving it would orphan the history pointing at it.
        @Test
        fun `keeps an existing id when the program is renamed`() {
            val base = Program("base", "Base", listOf(ProgramWeek.Training(1, workoutId = "push-day")))
            val draft = programDraftOf(base).copy(name = "  Winter block ")
            assertEquals(base.copy(name = "Winter block"), built(submitProgram(draft, base)))
        }

        @Test
        fun `derives a new program's id from its name`() {
            assertEquals("six-week-pull", built(submitProgram(named("Six Week Pull", 1), null)).id)
        }
    }

    @Nested
    inner class Weeks {
        // Counting would call the next one 2 and collide; max+1 keeps the numbering monotonic.
        @Test
        fun `numbers a new week past the highest already there, not by count`() {
            val draft =
                named("Base", 2)
                    .let { it.steppingWeek(it.keyAt(1), 3) }
                    .let { it.removingWeek(it.keyAt(0)) }
                    .addingWeek(workouts)
            assertEquals(listOf(5L, 6L), draft.weeks.map { it.week.week })
        }

        // An unset workout is a Save refusal, so the common path (one workout every week) needs no picker.
        @Test
        fun `defaults a new week to the first workout`() {
            assertEquals(ProgramWeek.Training(1, workoutId = "push-day"), named("Base", 1).weeks.single().week)
        }

        // 1-based throughout: the schema's `positive()` and week resolution both assume it.
        @Test
        fun `does not let a week number fall below one`() {
            val draft = named("Base", 1).let { it.steppingWeek(it.keyAt(0), -3) }
            assertEquals(
                1L,
                draft.weeks
                    .single()
                    .week.week,
            )
        }

        @Test
        fun `changes a week to another workout`() {
            val draft = named("Base", 1).let { it.choosingWorkout(it.keyAt(0), "pull-day") }
            assertEquals(ProgramWeek.Training(1, workoutId = "pull-day"), draft.weeks.single().week)
        }

        @Test
        fun `removes a week, and gives no later week a key already used`() {
            val draft = named("Base", 2).let { it.removingWeek(it.keyAt(0)) }.addingWeek(workouts)
            assertEquals(listOf(1, 2), draft.weeks.map { it.key })
        }

        // Two cards at week 1, told apart by day, are one week: "28 weeks" read beside "Weeks 1–4".
        @Test
        fun `counts weeks rather than week entries`() {
            val draft = named("Base", 3).let { it.steppingWeek(it.keyAt(1), -1) }
            assertEquals(2, draft.weekCount)
        }

        @Test
        fun `sets a week's overrides, and stores none as absent`() {
            val override = ProgramOverride.ExerciseOverride("pull-ups", mapOf("sets" to OverrideValue.Numeric(5.0)))
            val draft = named("Base", 1).let { it.withOverrides(it.keyAt(0), listOf(override)) }
            assertEquals(listOf(override), (draft.weeks.single().week as ProgramWeek.Training).overrides)
            val cleared = draft.withOverrides(draft.keyAt(0), emptyList())
            assertEquals(null, (cleared.weeks.single().week as ProgramWeek.Training).overrides)
        }
    }

    /**
     * Rest is a change of kind, not a flag: a rest week has no workout and no overrides, and the schema
     * refuses a file carrying both, so the toggle drops them rather than hiding them.
     */
    @Nested
    inner class RestDays {
        private val override = ProgramOverride.ExerciseOverride("pull-ups", mapOf("sets" to OverrideValue.Numeric(5.0)))

        @Test
        fun `drops the workout and the overrides, keeping the number, day and notes`() {
            val draft =
                named("Base", 1).let {
                    it
                        .choosingWorkout(it.keyAt(0), "pull-day")
                        .withOverrides(it.keyAt(0), listOf(override))
                        .withDay(it.keyAt(0), "Mon")
                        .withNotes(it.keyAt(0), "Easy")
                        .togglingRest(it.keyAt(0), workouts)
                }
            assertEquals(ProgramWeek.RestDay(1, "Mon", "Easy"), draft.weeks.single().week)
        }

        // The first workout again, as a new week gets: remembering the old one means a shadow field.
        @Test
        fun `gives a week a workout back when rest is switched off again`() {
            val draft =
                named("Base", 1).let {
                    it.choosingWorkout(it.keyAt(0), "pull-day").togglingRest(it.keyAt(0), workouts).togglingRest(
                        it.keyAt(0),
                        workouts,
                    )
                }
            assertEquals(ProgramWeek.Training(1, workoutId = "push-day"), draft.weeks.single().week)
        }

        @Test
        fun `persists a rest week with no workout on it`() {
            val draft = named("Base", 2).let { it.togglingRest(it.keyAt(1), workouts) }
            assertEquals(
                listOf(ProgramWeek.Training(1, workoutId = "push-day"), ProgramWeek.RestDay(2)),
                built(submitProgram(draft, null)).weeks,
            )
        }

        // As the schema refuses it: Home would show a rest card with no way past it.
        @Test
        fun `refuses a program of nothing but rest days`() {
            val draft = named("Base", 1).let { it.togglingRest(it.keyAt(0), workouts) }
            assertEquals(ProgramSubmit.Refused(ProgramProblem.NoTrainingWeek), submitProgram(draft, null))
        }

        @Test
        fun `chooses no workout for a rest week`() {
            val draft =
                named(
                    "Base",
                    1,
                ).let { it.togglingRest(it.keyAt(0), workouts).choosingWorkout(it.keyAt(0), "pull-day") }
            assertEquals(ProgramWeek.RestDay(1), draft.weeks.single().week)
        }
    }

    @Nested
    inner class InTheLibrary {
        private val base = Program("base", "Base", listOf(ProgramWeek.Training(1, workoutId = "push-day")))
        private val library = Library(1, emptyList(), workouts, listOf(base))

        @Test
        fun `replaces a program in place, and adds a new one at the end`() {
            val renamed = base.copy(name = "Winter")
            val other = base.copy(id = "other")
            assertEquals(listOf(renamed), library.withProgram(renamed).programs)
            assertEquals(listOf(base, other), library.withProgram(other).programs)
        }

        // Nothing in the library points at a program, so a delete needs no in-use guard.
        @Test
        fun `deletes a program`() {
            assertEquals(emptyList<Program>(), library.withoutProgram("base").programs)
        }
    }
}
