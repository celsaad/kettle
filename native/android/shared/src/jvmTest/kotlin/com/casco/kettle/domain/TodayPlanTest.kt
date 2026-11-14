package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/** Today's program position, week strip and hero summary (#412). */
class TodayPlanTest {
    @Nested
    inner class Position {
        // Weeks numbered by their author: "of 4" is the last one, not how many slots there are.
        @Test
        fun `reads the week the slot is in, of the highest week number`() {
            val program =
                Program(
                    "p",
                    "P",
                    listOf(
                        ProgramWeek.Training(1, "A", "w"),
                        ProgramWeek.Training(1, "B", "w"),
                        ProgramWeek.Training(4, "A", "w"),
                    ),
                )

            val position = ProgramPosition(program, 1)

            assertEquals(1L, position.weekNumber)
            assertEquals(4L, position.weekCount)
        }

        // The rotation sorts by week number, so a week written last in the file still runs in its turn.
        @Test
        fun `indexes the rotation, not the file`() {
            val program =
                Program("p", "P", listOf(ProgramWeek.Training(2, "A", "w"), ProgramWeek.Training(1, "A", "w")))

            assertEquals(1L, ProgramPosition(program, 0).weekNumber)
        }
    }

    @Nested
    inner class WeekStrip {
        private val program =
            Program(
                "p",
                "P",
                listOf(
                    ProgramWeek.Training(1, "Day 1", "w"),
                    ProgramWeek.RestDay(1, "Day 2"),
                    ProgramWeek.Training(1, "Day 3", "w"),
                    ProgramWeek.Training(2, "Day 1", "w"),
                    ProgramWeek.RestDay(2, "Day 2"),
                    ProgramWeek.Training(2, "Day 3", "w"),
                ),
            )

        private fun strip(index: Int) = weekStrip(ProgramPosition(program, index))

        @Test
        fun `holds only the current week's slots, numbered from one in file order`() {
            val slots = strip(4)

            assertEquals(listOf(1, 2, 3), slots.map { it.number })
            assertEquals(listOf("Day 1", "Day 2", "Day 3"), slots.map { it.day })
            assertEquals(listOf(false, true, false), slots.map { it.rest })
        }

        // Position, not completion (#408): a slot behind the card's is behind whether or not it ran.
        @Test
        fun `places every slot by where it stands against the card's`() {
            assertEquals(listOf(SlotPlace.Behind, SlotPlace.Current, SlotPlace.Ahead), strip(4).map { it.place })
        }

        @Test
        fun `starts a week with nothing behind`() {
            assertEquals(listOf(SlotPlace.Current, SlotPlace.Ahead, SlotPlace.Ahead), strip(3).map { it.place })
        }

        @Test
        fun `ends a week with everything behind`() {
            assertEquals(listOf(SlotPlace.Behind, SlotPlace.Behind, SlotPlace.Current), strip(5).map { it.place })
        }

        // A one-week program wraps onto itself: after the wrap the card is back on slot one, so
        // nothing is behind it, which is the truth about where the rotation is.
        @Test
        fun `has nothing behind after a wrap back to the first slot`() {
            val oneWeek = Program("p", "P", program.weeks.take(3))

            assertEquals(SlotPlace.Current, weekStrip(ProgramPosition(oneWeek, 0)).first().place)
        }
    }

    @Nested
    inner class Summary {
        private val squat = Exercise("squat", "Squat", ExerciseConfig.Reps(3, 5, targetWeightKg = 60.0, restSec = 90.0))
        private val pushup = Exercise("pushup", "Push-up", ExerciseConfig.Reps(2, 10, restSec = 60.0))
        private val plank = Exercise("plank", "Plank", ExerciseConfig.TimedHold(2, 30.0, restSec = 30.0))
        private val burpees = Exercise("burpees", "Burpees", ExerciseConfig.Hiit(40.0, 20.0, 8))
        private val breather = Exercise("breather", "Breather", ExerciseConfig.Rest(60.0))
        private val exercises = listOf(squat, pushup, plank, burpees, breather)

        private fun summaryOf(vararg blocks: WorkoutBlock) = planSummary(Workout("w", "W", blocks.toList()), exercises)

        private fun one(id: String) = WorkoutBlock.ExerciseBlock(id)

        @Test
        fun `lists each exercise with its load, and counts sets when all of it is sets`() {
            val summary = summaryOf(one("squat"), one("pushup"), one("plank"))

            assertEquals(
                listOf(
                    PlannedExercise("Squat", 60.0),
                    PlannedExercise("Push-up", null),
                    PlannedExercise("Plank", null),
                ),
                summary.exercises,
            )
            assertEquals(7, summary.sets)
        }

        // Eight HIIT rounds are no sets, and "11 sets" would be false.
        @Test
        fun `counts no sets once any of the work is something else`() {
            assertNull(summaryOf(one("squat"), one("burpees")).sets)
        }

        @Test
        fun `leaves a rest block out of both the list and the count`() {
            val summary = summaryOf(one("squat"), one("breather"))

            assertEquals(listOf("Squat"), summary.exercises.map { it.name })
            assertEquals(3, summary.sets)
        }

        @Test
        fun `lists a circuit's members once, however many rounds it runs`() {
            val summary =
                summaryOf(
                    WorkoutBlock.Circuit(rounds = 3, members = listOf(CircuitMember("squat"), CircuitMember("pushup"))),
                )

            assertEquals(listOf("Squat", "Push-up"), summary.exercises.map { it.name })
        }

        // As the workout is written: the same exercise in two blocks is two entries.
        @Test
        fun `lists the same exercise twice when two blocks run it`() {
            assertEquals(2, summaryOf(one("squat"), one("squat")).exercises.size)
        }

        // The card holds the workout with a program week's overrides applied, so the load is the week's.
        @Test
        fun `reads the load from the exercises it is given`() {
            val heavier = listOf(squat.copy(config = (squat.config as ExerciseConfig.Reps).copy(targetWeightKg = 70.0)))

            assertEquals(70.0, planSummary(Workout("w", "W", listOf(one("squat"))), heavier).exercises.single().loadKg)
        }

        @Test
        fun `treats a zero load as none`() {
            val bodyweight =
                listOf(squat.copy(config = (squat.config as ExerciseConfig.Reps).copy(targetWeightKg = 0.0)))

            assertNull(planSummary(Workout("w", "W", listOf(one("squat"))), bodyweight).exercises.single().loadKg)
        }

        // The runner's step ceiling cuts a workout this long short; the card's counts must not follow it.
        @Test
        fun `lists every exercise of a workout the step ceiling cuts short, and counts no sets`() {
            val marathon =
                Exercise("marathon", "Marathon", ExerciseConfig.Reps(MAX_STEPS_PER_EXERCISE.toLong(), 1, restSec = 0.0))
            val blocks = List(15) { WorkoutBlock.ExerciseBlock("marathon") }

            val summary = planSummary(Workout("w", "W", blocks), listOf(marathon))

            assertEquals(15, summary.exercises.size)
            assertNull(summary.sets)
        }

        @Test
        fun `is empty for a workout with nothing to run`() {
            val summary = summaryOf(one("ghost"))

            assertEquals(emptyList<PlannedExercise>(), summary.exercises)
            assertNull(summary.sets)
        }
    }
}
