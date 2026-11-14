package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The completion screen's "Against last time" (#416): each exercise against the last session that had it. */
class AgainstLastTimeTest {
    private val squat = Exercise("squat", "Squat", ExerciseConfig.Reps(3, 5, restSec = 90.0))

    private fun compare(
        today: Session,
        vararg log: Session,
    ) = againstLastTime(today, listOf(today) + log, listOf(squat))

    @Test
    fun `compares with the newest earlier session that had the exercise`() {
        val rows =
            compare(
                lift("today", "2026-08-15", 65.0),
                lift("older", "2026-08-01", 55.0),
                lift("last", "2026-08-08", 60.0),
            )

        assertEquals(5.0, rows.single().change)
        assertFalse(rows.single().firstTime)
        assertEquals("Squat", rows.single().exerciseName)
    }

    @Test
    fun `says first time when nothing before logged it`() {
        val row = compare(lift("today", "2026-08-15", 65.0)).single()

        assertTrue(row.firstTime)
        assertNull(row.change)
    }

    /** A later session in the log (edited back, or a clock moved) is not "last time". */
    @Test
    fun `ignores sessions that started after this one`() {
        val row = compare(lift("today", "2026-08-15", 65.0), lift("later", "2026-08-20", 80.0)).single()

        assertTrue(row.firstTime)
    }

    @Test
    fun `takes the better of two entries of one exercise, and shows that one`() {
        val today = session("today", "2026-08-15", reps("squat", 50.0), reps("squat", 70.0))
        val row = compare(today, lift("last", "2026-08-08", 60.0)).single()

        assertEquals(10.0, row.change)
        assertEquals(sessionEntryResult(reps("squat", 70.0)), row.result)
    }

    /** Bodyweight last time, loaded today: two measures, nothing to subtract. */
    @Test
    fun `has no change across two measures, but it isn't a first time`() {
        val bodyweight = SessionEntry.Reps("squat", listOf(RepsSetLog(reps = 10, restTakenSec = 60.0)))
        val row = compare(lift("today", "2026-08-15", 65.0), session("last", "2026-08-08", bodyweight)).single()

        assertNull(row.change)
        assertFalse(row.firstTime)
    }

    @Test
    fun `leaves rests out, and skips an unfinished earlier session`() {
        val today = session("today", "2026-08-15", reps("squat", 65.0), SessionEntry.Rest("rest", 60.0))
        val open = lift("open", "2026-08-10", 90.0, ended = false)

        val rows = compare(today, open, lift("last", "2026-08-08", 60.0))

        assertEquals(listOf("squat"), rows.map { it.exerciseId })
        assertEquals(5.0, rows.single().change)
    }
}

private fun lift(
    id: String,
    day: String,
    kg: Double,
    ended: Boolean = true,
) = session(id, day, reps("squat", kg), ended = ended)

private fun session(
    id: String,
    day: String,
    vararg entries: SessionEntry,
    ended: Boolean = true,
): Session {
    val at = "${day}T10:00:00.000Z"
    return Session(1, id, null, null, null, null, at, at.takeIf { ended }, entries.toList())
}

private fun reps(
    exercise: String,
    kg: Double,
) = SessionEntry.Reps(exercise, listOf(RepsSetLog(reps = 5, weightKg = kg, restTakenSec = 60.0)))
