package com.casco.kettle.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `historySessionsView`'s cases from RN's `history-views.test.ts`, which went with RN History
 * (#202). RN's asserted the finished labels; here the selector returns their numbers, and the words
 * are History's screen test's (`HistoryScreenTest`, in `pt`). The rest pin what RN's view did without
 * a case of its own: rest entries dropped, "mixed", the editable flag and the day in the reader's zone.
 */
class HistoryViewsTest {
    private val library =
        Library(
            1,
            listOf(Exercise("lsit", "L-Sit", ExerciseConfig.TimedHold(sets = 3, holdSecMin = 15.0, restSec = 60.0))),
            listOf(Workout("push-day", "Push day", emptyList())),
            emptyList(),
        )

    private fun session(
        vararg entries: SessionEntry,
        workout: String? = "push-day",
        startedAt: String = "2026-07-24T09:00:00.000Z",
        endedAt: String? = "2026-07-24T09:12:00.000Z",
    ) = Session(1, startedAt, workout, null, null, null, startedAt, endedAt, entries.toList())

    private fun view(session: Session) = historySessionsView(listOf(session), library, TimeZone.UTC).single()

    private fun hold(vararg secs: Double) = SessionEntry.TimedHold("lsit", secs.map { TimedHoldSetLog(it, 0.0) })

    @Test
    fun `counts the sets and minutes a row shows`() {
        val single = view(session(hold(20.0)))
        assertEquals(1, single.sets)
        assertEquals(12, single.minutes)

        assertEquals(2, view(session(hold(20.0, 18.0))).sets)
    }

    @Test
    fun `names a session after its workout, or nothing when it was started ad-hoc`() {
        assertEquals("Push day", view(session()).workoutName)
        assertNull(view(session(workout = null)).workoutName)
    }

    // An ad-hoc session has no workout to name it, and counts like any other: the null path is pinned
    // so a later "simplification" of it fails here rather than crashing History.
    @Test
    fun `counts an ad-hoc session towards the totals like any other`() {
        val adHoc = session(hold(20.0), workout = null, endedAt = "2026-07-24T09:45:00.000Z")

        assertNull(view(adHoc).workoutName)
        assertEquals(HistoryStats(sessions = 1, hours = 0, minutes = 45, sets = 1), historyStats(listOf(adHoc)))
    }

    // A workout deleted from the library since is still the user's own words for it.
    @Test
    fun `falls back to the workout's id once it is gone from the library`() {
        assertEquals("pull-day", view(session(workout = "pull-day")).workoutName)
    }

    @Test
    fun `lists each entry by exercise name with what it put up, leaving rests out`() {
        val row =
            view(
                session(
                    hold(20.0, 18.5),
                    SessionEntry.Rest("rest", 60.0),
                    SessionEntry.Reps("gone", listOf(RepsSetLog(reps = 8, restTakenSec = 0.0))),
                ),
            )

        assertEquals(
            listOf(
                HistoryEntryView("L-Sit", EntryResult.Holds(listOf(20.0, 18.5))),
                HistoryEntryView("gone", EntryResult.Reps(listOf(8L))),
            ),
            row.entries,
        )
    }

    @Test
    fun `calls a session mixed only when it logged more than one kind of work`() {
        assertFalse(view(session(hold(20.0), SessionEntry.Rest("rest", 60.0), hold(10.0))).mixed)
        assertTrue(view(session(hold(20.0), SessionEntry.Hiit("burpees", 4))).mixed)
    }

    // The runner owns a running session's file and writes through its own copy, so an edit would be lost.
    @Test
    fun `offers editing only once a session has finished`() {
        assertTrue(view(session()).editable)
        assertFalse(view(session(endedAt = null)).editable)
    }

    @Test
    fun `dates a session by the day it started where the reader is`() {
        val late = session(startedAt = "2026-07-24T23:30:00.000Z")

        assertEquals(LocalDate(2026, 7, 24), view(late).day)
        assertEquals(
            LocalDate(2026, 7, 25),
            historySessionsView(listOf(late), library, TimeZone.of("Asia/Tokyo")).single().day,
        )
    }

    @Test
    fun `reduces every entry type to what it put up`() {
        assertEquals(EntryResult.Rounds(5, null), sessionEntryResult(SessionEntry.Hiit("b", 5)))
        assertEquals(EntryResult.Rounds(7, 3), sessionEntryResult(SessionEntry.Amrap("g", 7, extraReps = 3)))
        assertEquals(
            EntryResult.Intervals(3, 20),
            sessionEntryResult(SessionEntry.Emom("s", listOf(EmomMinuteLog(10), EmomMinuteLog(10), EmomMinuteLog()))),
        )
        // No reps entered at all is no total, not a total of 0.
        assertEquals(
            EntryResult.Intervals(2, null),
            sessionEntryResult(SessionEntry.Emom("s", List(2) { EmomMinuteLog() })),
        )
        assertEquals(
            EntryResult.Cardio(600.0, null),
            sessionEntryResult(SessionEntry.Cardio("row", durationSec = 600.0)),
        )
        assertEquals(EntryResult.Rest(90.0), sessionEntryResult(SessionEntry.Rest("rest", 90.0)))
    }
}
