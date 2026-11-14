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

        assertEquals(mapOf("b" to "squat"), records)
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

        assertEquals(mapOf("late" to "squat"), records)
    }

    @Test
    fun `gives a tie no record`() {
        assertEquals(
            emptyMap<String, String>(),
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

        assertEquals(mapOf("b" to "squat"), records)
    }

    @Test
    fun `names the first entry that set one, in the order logged`() {
        val first = session("a", "2026-08-01", reps("squat", 60.0), reps("press", 30.0))
        val second = session("b", "2026-08-08", reps("squat", 60.0), reps("press", 32.5), reps("row", 40.0))

        assertEquals(mapOf("b" to "press"), recordsAtTheTime(listOf(first, second)))
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
