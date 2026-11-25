package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * What the exercise editor (#241) does to the library, and what it shows of the log: RN's store's
 * `saveExercise`/`deleteExercise`, the delete guard the screen ran inline, and `exerciseHistory`, whose
 * two cases are the volume ones below.
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
            assertEquals(
                600.0,
                exerciseHistory(listOf(session("a", bench)), "bench", UnitSystem.METRIC).single().volume,
            )
            assertEquals(
                18.0,
                exerciseHistory(listOf(session("a", bodyweight)), "pushups", UnitSystem.METRIC).single().volume,
            )
        }

        // The chart prints this beside fields in the reader's unit, so a pound reader saw kilograms (#271).
        @Test
        fun `gives a weighted volume in the reader's unit`() {
            val imperial = exerciseHistory(listOf(session("a", bench)), "bench", UnitSystem.IMPERIAL).single()
            // 60 kg shows as 132.3 lb; two sets of five of that.
            assertEquals(132.3, toDisplayWeight(60.0, UnitSystem.IMPERIAL))
            assertEquals(1323.0, imperial.volume, 1e-9)
        }

        @Test
        fun `leaves volumes that aren't weights the same in either unit`() {
            val entries =
                listOf(
                    SessionEntry.Reps("pushups", listOf(RepsSetLog(10, restTakenSec = 45.0))),
                    SessionEntry.TimedHold("plank", listOf(TimedHoldSetLog(45.0, restTakenSec = 60.0))),
                    SessionEntry.Cardio("row", 480.0, 2000.0),
                    SessionEntry.Cardio("bike", 600.0),
                )
            for (entry in entries) {
                assertEquals(
                    entryVolume(entry, UnitSystem.METRIC),
                    entryVolume(entry, UnitSystem.IMPERIAL),
                    entry.exercise,
                )
            }
        }

        @Test
        fun `uses distance over duration for cardio, falling back to duration when distance is absent`() {
            val withDistance = SessionEntry.Cardio("row", 480.0, 2000.0)
            val durationOnly = SessionEntry.Cardio("row", 480.0)
            assertEquals(
                2000.0,
                exerciseHistory(listOf(session("a", withDistance)), "row", UnitSystem.METRIC).single().volume,
            )
            assertEquals(
                480.0,
                exerciseHistory(listOf(session("a", durationOnly)), "row", UnitSystem.METRIC).single().volume,
            )
        }

        @Test
        fun `lists the last five times, newest first as the log is, skipping unfinished sessions`() {
            val log =
                listOf(session("running", bench, endedAt = null)) + (1..6).map { session("s$it", bench) }
            assertEquals(
                listOf("s1", "s2", "s3", "s4", "s5"),
                exerciseHistory(log, "bench", UnitSystem.METRIC).map { it.sessionId },
            )
        }

        // A rest's repeat performance isn't the way a lift's is: a rest exercise has no history to show.
        @Test
        fun `leaves rest entries out`() {
            assertTrue(
                exerciseHistory(
                    listOf(session("a", SessionEntry.Rest("rest", 60.0))),
                    "rest",
                    UnitSystem.METRIC,
                ).isEmpty(),
            )
        }

        @Test
        fun `carries when it started and what was done`() {
            val entry = exerciseHistory(listOf(session("a", bench)), "bench", UnitSystem.METRIC).single()
            assertEquals(Instant.parse("2026-07-24T09:00:00Z"), entry.startedAt)
            assertEquals(sessionEntryResult(bench), entry.result)
        }
    }
}
