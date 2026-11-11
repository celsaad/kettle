package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * What the exercise editor (#241) does to the library, and what it shows of the log: RN's store's
 * `saveExercise`/`deleteExercise`, the delete guard `exercise-editor.tsx` ran inline, and
 * `history-views.ts`' `exerciseHistory`, whose two jest cases are the volume ones below.
 */
class ExerciseEditingTest {
    private val pullUps =
        Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 3, targetRepsMin = 8, restSec = 90.0))
    private val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(sets = 3, targetRepsMin = 10, restSec = 90.0))

    private fun library(
        exercises: List<Exercise> = listOf(pullUps, dips),
        workouts: List<Workout> = emptyList(),
    ) = Library(1, exercises, workouts, emptyList())

    @Nested
    inner class Saving {
        @Test
        fun `replaces an edited exercise where it stands`() {
            val renamed = pullUps.copy(name = "Chin-ups")
            assertEquals(listOf(renamed, dips), library().withExercise(renamed).exercises)
        }

        @Test
        fun `adds a new exercise at the end`() {
            val plank = Exercise("plank", "Plank", ExerciseConfig.TimedHold(sets = 3, restSec = 60.0))
            assertEquals(listOf(pullUps, dips, plank), library().withExercise(plank).exercises)
        }

        @Test
        fun `deletes by id and leaves the rest`() {
            assertEquals(listOf(dips), library().withoutExercise("pull-ups").exercises)
        }
    }

    /** The runner's adopt (#220), ported from the RN store's `setTargetWeightKg` cases. */
    @Nested
    inner class TargetWeight {
        private val plank =
            Exercise("plank", "Plank", ExerciseConfig.TimedHold(sets = 3, holdSecMin = 45.0, restSec = 60.0))

        @Test
        fun `sets the target load in kilograms, unconverted, and keeps the rest`() {
            val set = library(listOf(pullUps, dips, plank)).withTargetWeight("pull-ups", 62.5)

            val config = pullUps.config as ExerciseConfig.Reps
            assertEquals(library(listOf(pullUps.copy(config = config.copy(targetWeightKg = 62.5)), dips, plank)), set)
        }

        @Test
        fun `sets nothing on an exercise with no target weight, or one that isn't there`() {
            val library = library(listOf(pullUps, plank))

            assertNull(library.withTargetWeight("plank", 20.0))
            assertNull(library.withTargetWeight("gone", 20.0))
        }
    }

    @Nested
    inner class InUse {
        @Test
        fun `names a workout that uses the exercise as a plain block`() {
            val push = Workout("push", "Push day", listOf(WorkoutBlock.ExerciseBlock("pull-ups")))
            assertEquals(listOf(push), workoutsUsing(library(workouts = listOf(push)), "pull-ups"))
        }

        // The branch a click-through misses: a circuit member is a second, differently shaped reference.
        @Test
        fun `also names one whose only use is inside a circuit`() {
            val circuit =
                WorkoutBlock.Circuit(rounds = 3, members = listOf(CircuitMember("pull-ups"), CircuitMember("dips")))
            val push = Workout("push", "Push day", listOf(circuit))
            assertEquals(listOf(push), workoutsUsing(library(workouts = listOf(push)), "pull-ups"))
        }

        @Test
        fun `names nothing for an exercise no workout uses`() {
            val push = Workout("push", "Push day", listOf(WorkoutBlock.ExerciseBlock("dips")))
            assertTrue(workoutsUsing(library(workouts = listOf(push)), "pull-ups").isEmpty())
        }
    }

    @Nested
    inner class History {
        private fun session(
            id: String,
            vararg entries: SessionEntry,
            endedAt: String? = "2026-07-24T10:00:00.000Z",
        ) = Session(1, id, null, null, null, null, "2026-07-24T09:00:00.000Z", endedAt, entries.toList())

        private val bench =
            SessionEntry.Reps(
                "bench",
                listOf(RepsSetLog(5, 60.0, restTakenSec = 90.0), RepsSetLog(5, 60.0, restTakenSec = 90.0)),
            )

        @Test
        fun `weights reps volume by load only when a set actually carries weight`() {
            val bodyweight =
                SessionEntry.Reps(
                    "pushups",
                    listOf(RepsSetLog(10, restTakenSec = 45.0), RepsSetLog(8, restTakenSec = 0.0)),
                )
            assertEquals(600.0, exerciseHistory(listOf(session("a", bench)), "bench").single().volume)
            assertEquals(18.0, exerciseHistory(listOf(session("a", bodyweight)), "pushups").single().volume)
        }

        @Test
        fun `uses distance over duration for cardio, falling back to duration when distance is absent`() {
            val withDistance = SessionEntry.Cardio("row", 480.0, 2000.0)
            val durationOnly = SessionEntry.Cardio("row", 480.0)
            assertEquals(2000.0, exerciseHistory(listOf(session("a", withDistance)), "row").single().volume)
            assertEquals(480.0, exerciseHistory(listOf(session("a", durationOnly)), "row").single().volume)
        }

        @Test
        fun `lists the last five times, newest first as the log is, skipping unfinished sessions`() {
            val log =
                listOf(session("running", bench, endedAt = null)) + (1..6).map { session("s$it", bench) }
            assertEquals(
                listOf("s1", "s2", "s3", "s4", "s5"),
                exerciseHistory(log, "bench").map { it.sessionId },
            )
        }

        // A rest's repeat performance isn't the way a lift's is: a rest exercise has no history to show.
        @Test
        fun `leaves rest entries out`() {
            assertTrue(exerciseHistory(listOf(session("a", SessionEntry.Rest("rest", 60.0))), "rest").isEmpty())
        }

        @Test
        fun `carries when it started and what was done`() {
            val entry = exerciseHistory(listOf(session("a", bench)), "bench").single()
            assertEquals(Instant.parse("2026-07-24T09:00:00Z"), entry.startedAt)
            assertEquals(sessionEntryResult(bench), entry.result)
        }
    }

    /** Where the Recent chart's bars go, as RN's `volume-chart.tsx` placed them. */
    @Nested
    inner class Chart {
        @Test
        fun `fills the bar area with the tallest bar and scales the rest to it`() {
            val bars = volumeChartLayout(listOf(300.0, 600.0), 200f)
            assertEquals(listOf(28f, 56f), bars.map { it.height })
            // Each stands on the line above the date labels, at the chart's bottom less their 18.
            assertEquals(listOf(50f, 22f), bars.map { it.top })
        }

        @Test
        fun `centres each bar in an equal share of the width`() {
            val bars = volumeChartLayout(listOf(1.0, 1.0, 1.0, 1.0), 200f)
            assertEquals(listOf(25f, 75f, 125f, 175f), bars.map { it.centerX })
            // A share of 50 less 8 of gap is 42, past the widest a bar gets.
            assertEquals(40f, bars.first().width)
        }

        @Test
        fun `keeps a bar wide enough to see when the shares are narrow`() {
            assertEquals(16f, volumeChartLayout(List(10) { 1.0 }, 100f).first().width)
        }

        // A session that moved nothing still shows it was there.
        @Test
        fun `draws a zero as a sliver, and an all-zero chart without dividing by zero`() {
            assertEquals(listOf(2f, 2f), volumeChartLayout(listOf(0.0, 0.0), 100f).map { it.height })
        }
    }
}
