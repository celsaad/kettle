package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * `src/domain/__tests__/logged-work.test.ts` and `src/state/selectors/__tests__/logged-sessions.test.ts`,
 * case for case. Logging 0 is how an exercise is skipped in the runner, so a zero set shown in History
 * or counted as a set reads as work that never happened.
 */
class LoggedWorkTest {
    private fun session(
        startedAt: String,
        vararg entries: SessionEntry,
        endedAt: String? = startedAt,
    ) = Session(1, startedAt, null, null, null, null, startedAt, endedAt, entries.toList())

    private fun reps(vararg counts: Long) =
        SessionEntry.Reps("pullups", counts.map { RepsSetLog(reps = it, restTakenSec = 90.0) })

    @Nested
    inner class EntryWithoutZeroWork {
        @Test
        fun `drops zero sets from a reps or hold entry and keeps the rest`() {
            assertEquals(reps(6), entryWithoutZeroWork(reps(6, 0)))
            assertEquals(
                SessionEntry.TimedHold("plank", listOf(TimedHoldSetLog(2.0, 0.0))),
                entryWithoutZeroWork(
                    SessionEntry.TimedHold("plank", listOf(TimedHoldSetLog(0.0, 0.0), TimedHoldSetLog(2.0, 0.0))),
                ),
            )
        }

        @Test
        fun `returns null when nothing of the entry is left`() {
            assertNull(entryWithoutZeroWork(reps(0)))
            assertNull(entryWithoutZeroWork(reps()))
            assertNull(entryWithoutZeroWork(SessionEntry.TimedHold("plank", listOf(TimedHoldSetLog(0.0, 0.0)))))
            assertNull(entryWithoutZeroWork(SessionEntry.Amrap("grinder", roundsCompleted = 0)))
            assertNull(entryWithoutZeroWork(SessionEntry.Cardio("row", durationSec = 0.0)))
        }

        @Test
        fun `keeps an AMRAP or cardio entry with any work in it`() {
            val extraOnly = SessionEntry.Amrap("grinder", roundsCompleted = 0, extraReps = 5)
            val distanceOnly = SessionEntry.Cardio("row", durationSec = 0.0, distanceMeters = 500.0)

            assertSame(extraOnly, entryWithoutZeroWork(extraOnly))
            assertSame(distanceOnly, entryWithoutZeroWork(distanceOnly))
        }

        // An EMOM minute with no reps entered was still worked, and a HIIT round only counts once finished.
        @Test
        fun `leaves EMOM, HIIT and rest entries alone`() {
            val entries =
                listOf(
                    SessionEntry.Emom("swings", listOf(EmomMinuteLog(), EmomMinuteLog())),
                    SessionEntry.Hiit("burpees", roundsCompleted = 0),
                    SessionEntry.Rest("rest", restTakenSec = 0.0),
                )
            for (entry in entries) assertSame(entry, entryWithoutZeroWork(entry))
        }
    }

    @Nested
    inner class WithoutZeroWork {
        @Test
        fun `returns the same session when there is nothing to remove`() {
            val session = session("2026-07-24T09:00:00.000Z", reps(6))

            assertSame(session, withoutZeroWork(session))
        }

        @Test
        fun `removes zero sets, then entries left empty by that`() {
            val dips = SessionEntry.Reps("dips", listOf(RepsSetLog(reps = 8, restTakenSec = 0.0)))
            val session = session("2026-07-24T09:00:00.000Z", reps(0), dips)

            assertEquals(listOf(dips), withoutZeroWork(session).entries)
        }
    }

    @Nested
    inner class HasLoggedWork {
        // A workout whose every exercise was skipped still logs its rest blocks, and that isn't a training day.
        @Test
        fun `does not count standalone rests as work`() {
            assertFalse(hasLoggedWork(session("2026-07-24T09:00:00.000Z", SessionEntry.Rest("rest", 60.0))))
            assertFalse(hasLoggedWork(session("2026-07-24T09:00:00.000Z")))
        }
    }

    @Nested
    inner class LoggedSessions {
        private val skipped = reps(0)
        private val done = SessionEntry.Reps("dips", listOf(RepsSetLog(reps = 8, restTakenSec = 0.0)))

        @Test
        fun `hides a finished session with no work left in it`() {
            val allSkipped = session("2026-07-24T09:00:00.000Z", skipped)
            val kept = session("2026-07-25T09:00:00.000Z", skipped, done)

            val logged = loggedSessions(listOf(kept, allSkipped))

            assertEquals(listOf(kept.id), logged.map { it.id })
            assertEquals(listOf(done), logged[0].entries)
        }

        // An in-flight session starts with no entries at all; hiding it would hide the session being run.
        @Test
        fun `keeps an unfinished session even while it is empty`() {
            val running = session("2026-07-24T09:00:00.000Z", endedAt = null)

            assertEquals(listOf(running), loggedSessions(listOf(running)))
        }

        // RN memoizes for its zustand selectors; here it's a `remember` key, which a fresh list would miss.
        @Test
        fun `returns the same list for the same log`() {
            val log = SessionLog(listOf(session("2026-07-24T09:00:00.000Z", done)), emptyList())

            assertSame(log.logged, log.logged)
        }
    }
}
