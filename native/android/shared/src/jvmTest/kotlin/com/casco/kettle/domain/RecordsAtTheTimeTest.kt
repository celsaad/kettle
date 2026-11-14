package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Sessions' PR badge (#414): a record at the time, judged against the sessions before it only, in the
 * order they started, by `sessionRecords`' rule (strictly greater, no first-ever record).
 */
class RecordsAtTheTimeTest {
    @Test
    fun `marks a session that beat every one before it, and not the first ever`() {
        val records = recordsAtTheTime(listOf(lift("a", "2026-08-01", 60.0), lift("b", "2026-08-08", 62.5)))

        assertEquals(mapOf("b" to 0), records)
    }

    /** `sessionRecords` against the whole log would take this badge away once a later session beat it. */
    @Test
    fun `keeps a record that a later session beat`() {
        val records =
            recordsAtTheTime(
                listOf(lift("a", "2026-08-01", 60.0), lift("b", "2026-08-08", 62.5), lift("c", "2026-08-15", 65.0)),
            )

        assertEquals(setOf("b", "c"), records.keys)
    }

    @Test
    fun `judges in the order sessions started, not the order they are listed`() {
        val records = recordsAtTheTime(listOf(lift("late", "2026-08-15", 65.0), lift("early", "2026-08-01", 60.0)))

        assertEquals(mapOf("late" to 0), records)
    }

    @Test
    fun `gives a tie no record`() {
        assertEquals(
            emptyMap<String, Int>(),
            recordsAtTheTime(listOf(lift("a", "2026-08-01", 60.0), lift("b", "2026-08-08", 60.0))),
        )
    }

    /** An unfinished session is neither a record nor the best a later one has to beat. */
    @Test
    fun `leaves out a session never finished`() {
        val records =
            recordsAtTheTime(
                listOf(
                    lift("a", "2026-08-01", 60.0),
                    lift("open", "2026-08-05", 80.0, ended = false),
                    lift("b", "2026-08-08", 62.5),
                ),
            )

        assertEquals(mapOf("b" to 0), records)
    }

    @Test
    fun `names the first entry that set one, in the order logged`() {
        val first = session("a", "2026-08-01", reps("squat", 60.0), reps("press", 30.0))
        val second = session("b", "2026-08-08", reps("squat", 60.0), reps("press", 32.5), reps("row", 40.0))

        assertEquals(mapOf("b" to 1), recordsAtTheTime(listOf(first, second)))
    }

    /** One exercise logged twice: the entry that beat the best is the one named, not the first of its exercise. */
    @Test
    fun `names the entry that set it when its exercise was logged twice`() {
        val first = session("a", "2026-08-01", reps("squat", 70.0))
        val second = session("b", "2026-08-08", reps("squat", 50.0), reps("squat", 80.0))

        assertEquals(mapOf("b" to 1), recordsAtTheTime(listOf(first, second)))
    }

    /** Two sessions at one instant: the id decides which is judged first, not where the file lists it. */
    @Test
    fun `takes sessions that started together in id order`() {
        val base = lift("a", "2026-08-01", 60.0)
        val listed = listOf(base, lift("y", "2026-08-08", 65.0), lift("x", "2026-08-08", 62.5))

        assertEquals(mapOf("x" to 0, "y" to 0), recordsAtTheTime(listed))
        assertEquals(mapOf("x" to 0, "y" to 0), recordsAtTheTime(listed.reversed()))
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
