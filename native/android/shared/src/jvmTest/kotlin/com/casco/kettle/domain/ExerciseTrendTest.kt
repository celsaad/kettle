package com.casco.kettle.domain

import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Exercise detail's Progress (#413): Stats' row when there is one, and otherwise which floor left the
 * exercise out, so a trained exercise is never called untrained.
 */
class ExerciseTrendTest {
    private val now = Instant.parse("2026-08-18T12:00:00.000Z")

    private fun trend(
        id: String,
        vararg sessions: Session,
        exercises: List<Exercise> = emptyList(),
    ) = exerciseTrend(sessions.toList(), exercises, id, now = now, zone = TimeZone.UTC)

    @Test
    fun `is Stats' own row when Getting stronger has one`() {
        val result = trend("rdl", logged("2026-08-04", reps("rdl", 10.0)), logged("2026-08-17", reps("rdl", 12.0)))

        assertTrue(result is ExerciseTrend.Charted && result.progress.latest == 12.0)
    }

    @Test
    fun `is untrained with nothing logged in the window`() {
        assertEquals(ExerciseTrend.Untrained, trend("rdl", logged("2026-06-01", reps("rdl", 10.0))))
    }

    @Test
    fun `is untrained when the only session in the window was never finished`() {
        assertEquals(ExerciseTrend.Untrained, trend("rdl", logged("2026-08-17", reps("rdl", 10.0), ended = false)))
    }

    @Test
    fun `is too few, not untrained, when trained once in the window`() {
        assertEquals(ExerciseTrend.TooFew, trend("rdl", logged("2026-08-17", reps("rdl", 10.0))))
    }

    @Test
    fun `is fixed, not untrained, for a fixed-length hold trained in the window`() {
        val hurdler =
            Exercise("hurdler", "Hurdler", ExerciseConfig.TimedHold(1, holdSecMin = 45.0, restSec = 30.0))
        val held =
            listOf("2026-08-11", "2026-08-17").map {
                logged(it, SessionEntry.TimedHold("hurdler", listOf(TimedHoldSetLog(45.0, restTakenSec = 30.0))))
            }

        assertEquals(ExerciseTrend.Fixed, trend("hurdler", *held.toTypedArray(), exercises = listOf(hurdler)))
    }

    /** Real numbers, unlike a fixed hold's copy of its config, but no single one to follow: not "fixed". */
    @Test
    fun `is unmeasured, not fixed, for interval and cardio work`() {
        val entries =
            listOf(
                SessionEntry.Hiit("burpees", 3),
                SessionEntry.Emom("swings", listOf(EmomMinuteLog(reps = 10), EmomMinuteLog(reps = 12))),
                SessionEntry.Cardio("run", durationSec = 1500.0, distanceMeters = 5000.0),
            )

        entries.forEach { entry ->
            val weekly = listOf("2026-08-10", "2026-08-17").map { logged(it, entry) }
            assertEquals(ExerciseTrend.Unmeasured, trend(entry.exercise, *weekly.toTypedArray()), entry.exercise)
        }
    }
}

private fun logged(
    day: String,
    vararg entries: SessionEntry,
    ended: Boolean = true,
): Session {
    val at = "${day}T10:00:00.000Z"
    return Session(1, at, null, null, null, null, at, at.takeIf { ended }, entries.toList())
}

private fun reps(
    exercise: String,
    weightKg: Double,
) = SessionEntry.Reps(exercise, listOf(RepsSetLog(reps = 8, weightKg = weightKg, restTakenSec = 60.0)))
