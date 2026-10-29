package com.casco.kettle.domain

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * `src/state/selectors/__tests__/history-stats.test.ts`, case for case, which went with RN History (#202):
 * this suite is the spec now.
 *
 * Where jest fakes the clock and mocks the device's first weekday, these pass both, with the zone:
 * local days in New York, the DST-observing zone jest's TZ pin names, so the DST cases bite here on
 * every machine rather than only where TZ is honoured.
 */
class HistoryStatsTest {
    private val zone = TimeZone.of("America/New_York")

    /** [year]-[month]-[day] at [hour] in [zone], as jest's `new Date(y, m - 1, d, h)`. */
    private fun local(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Instant = LocalDateTime(year, month, day, hour, minute).toInstant(zone)

    private fun session(
        startedAt: Instant,
        endedAt: Instant? = startedAt,
        id: String = startedAt.toString(),
    ) = Session(1, id, null, null, null, null, startedAt.toString(), endedAt?.toString(), emptyList())

    private fun session(
        startedAt: String,
        endedAt: String,
    ) = Session(1, startedAt, null, null, null, null, startedAt, endedAt, emptyList())

    @Nested
    inner class HistoryStatsTotals {
        // Regression: hours/minutes must be the two whole-number halves of one "1h 30m" reading. RN once
        // computed hours as a fractional round(total/60*10)/10, so 90 minutes rendered "1.5h 30m".
        @Test
        fun `splits a 90-minute total into whole hours and a remainder, not a fractional hour`() {
            val stats = historyStats(listOf(session("2026-07-24T09:00:00.000Z", "2026-07-24T10:30:00.000Z")))

            assertEquals(HistoryStats(sessions = 1, hours = 1, minutes = 30, sets = 0), stats)
        }

        @Test
        fun `reports zeroes for no sessions`() {
            assertEquals(HistoryStats(sessions = 0, hours = 0, minutes = 0, sets = 0), historyStats(emptyList()))
        }

        @Test
        fun `reports 0 hours for a sub-hour total`() {
            val stats = historyStats(listOf(session("2026-07-24T09:00:00.000Z", "2026-07-24T09:25:00.000Z")))

            assertEquals(HistoryStats(sessions = 1, hours = 0, minutes = 25, sets = 0), stats)
        }
    }

    /**
     * `sets` is the count behind History's summary line and each row's "N sets", asserted through
     * [historyStats] as jest does. Regression: interval entries used to count as a flat 1 apiece, so a
     * 16-round HIIT was worth the same as one hold.
     */
    @Nested
    inner class HistoryStatsSets {
        private fun setsIn(vararg entries: SessionEntry) =
            historyStats(
                listOf(
                    Session(1, "s", null, null, null, null, "2026-07-24T09:00:00.000Z", null, entries.toList()),
                ),
            ).sets

        private val pullups =
            SessionEntry.Reps(
                "pullups",
                listOf(RepsSetLog(reps = 8, restTakenSec = 90.0), RepsSetLog(reps = 6, restTakenSec = 0.0)),
            )

        @Test
        fun `counts one set per logged set for reps and holds`() {
            assertEquals(3, setsIn(pullups, SessionEntry.TimedHold("lsit", listOf(TimedHoldSetLog(20.0, 0.0)))))
        }

        @Test
        fun `counts each completed round of a HIIT or AMRAP entry`() {
            assertEquals(8, setsIn(SessionEntry.Hiit("burpees", roundsCompleted = 8)))
            assertEquals(5, setsIn(SessionEntry.Amrap("chipper", roundsCompleted = 5, extraReps = 3)))
        }

        @Test
        fun `counts each interval of an EMOM entry`() {
            assertEquals(
                3,
                setsIn(SessionEntry.Emom("swings", listOf(EmomMinuteLog(10), EmomMinuteLog(10), EmomMinuteLog()))),
            )
        }

        @Test
        fun `counts cardio as one effort and rest as none`() {
            assertEquals(1, setsIn(SessionEntry.Cardio("row", durationSec = 600.0, distanceMeters = 2000.0)))
            assertEquals(0, setsIn(SessionEntry.Rest("rest", restTakenSec = 90.0)))
        }

        @Test
        fun `sums a mixed session across all of them`() {
            assertEquals(
                12,
                setsIn(
                    pullups,
                    SessionEntry.Rest("rest", restTakenSec = 60.0),
                    SessionEntry.Hiit("burpees", roundsCompleted = 8),
                    SessionEntry.Emom("swings", listOf(EmomMinuteLog(10), EmomMinuteLog(10))),
                ),
            )
        }

        // Nothing performed is nothing counted: an abandoned interval block mustn't inflate the line.
        @Test
        fun `counts a round-less interval entry as no sets`() {
            assertEquals(0, setsIn(SessionEntry.Hiit("burpees", roundsCompleted = 0)))
        }
    }

    @Nested
    inner class CurrentStreak {
        private fun streak(
            now: Instant,
            vararg days: Instant,
        ) = currentStreak(days.map { session(it) }, now, zone)

        @Test
        fun `counts a session logged today only`() {
            assertEquals(1, streak(local(2026, 7, 24, 12), local(2026, 7, 24, 9)))
        }

        @Test
        fun `does not break the streak when only yesterday has a session and today has none yet`() {
            assertEquals(1, streak(local(2026, 7, 24, 8), local(2026, 7, 23, 9)))
        }

        @Test
        fun `counts a consecutive multi-day run`() {
            assertEquals(
                3,
                streak(local(2026, 7, 24, 12), local(2026, 7, 24, 9), local(2026, 7, 23, 9), local(2026, 7, 22, 9)),
            )
        }

        @Test
        fun `stops at a gap rather than counting an older isolated day`() {
            // Yesterday (07-23) has no session: the gap stops the streak before reaching 07-22.
            assertEquals(1, streak(local(2026, 7, 24, 12), local(2026, 7, 24, 9), local(2026, 7, 22, 9)))
        }

        @Test
        fun `returns 0 for no sessions at all`() {
            assertEquals(0, streak(local(2026, 7, 24, 12)))
        }

        // US spring-forward 2026 is Sunday 8 March, a 23-hour day. A fixed 24-hour step back from Monday
        // lands on Saturday and truncates the streak to 1.
        @Test
        fun `counts every day through a 23-hour spring-forward day`() {
            assertEquals(
                3,
                streak(
                    Instant.parse("2026-03-09T18:00:00-04:00"),
                    Instant.parse("2026-03-09T12:00:00-04:00"),
                    Instant.parse("2026-03-08T12:00:00-05:00"),
                    Instant.parse("2026-03-07T12:00:00-05:00"),
                ),
            )
        }

        // Autumn 2026 falls back on Sunday 1 November, a 25-hour day, where a fixed step stalls.
        @Test
        fun `counts every day through a 25-hour fall-back day`() {
            assertEquals(
                3,
                streak(
                    Instant.parse("2026-11-02T18:00:00-05:00"),
                    Instant.parse("2026-11-02T12:00:00-05:00"),
                    Instant.parse("2026-11-01T12:00:00-04:00"),
                    Instant.parse("2026-10-31T12:00:00-04:00"),
                ),
            )
        }
    }

    /**
     * The week's first day is the device calendar's rather than a hardcoded Monday, so both conventions
     * are driven: Brazil and the US start on Sunday, most of Europe on Monday.
     */
    @Nested
    inner class ThisWeekStats {
        private fun count(
            now: Instant,
            firstWeekday: DayOfWeek,
            vararg starts: Instant,
        ) = thisWeekStats(starts.map { session(it) }, now, zone, firstWeekday).sessions

        @Test
        fun `starts the week on Monday when the calendar says so`() {
            // Sunday 2026-07-26; the week began Monday 2026-07-20.
            val sessions = arrayOf(local(2026, 7, 20, 8), local(2026, 7, 26, 10), local(2026, 7, 19, 20))

            assertEquals(2, count(local(2026, 7, 26, 15), DayOfWeek.MONDAY, *sessions))
        }

        @Test
        fun `starts the week on Sunday when the calendar says so`() {
            // Sunday 2026-07-26 is now the week's first day: last Monday and the Sunday before are out.
            val sessions = arrayOf(local(2026, 7, 26, 10), local(2026, 7, 20, 8), local(2026, 7, 19, 20))

            assertEquals(1, count(local(2026, 7, 26, 15), DayOfWeek.SUNDAY, *sessions))
        }

        @Test
        fun `excludes the prior week once the boundary day arrives`() {
            val sessions = arrayOf(local(2026, 7, 26, 23), local(2026, 7, 27, 0, 5))

            assertEquals(1, count(local(2026, 7, 27, 9), DayOfWeek.MONDAY, *sessions))
        }
    }

    /**
     * The fixture is a Wednesday, so a session sits mid-week whichever weekday the calendar starts on,
     * except where the first weekday is the thing under test, and then it is set explicitly.
     */
    @Nested
    inner class TrainingCalendar {
        private val wednesday = local(2026, 8, 12, 12)

        private fun calendar(
            sessions: List<Session>,
            weeks: Int,
            now: Instant = wednesday,
            firstWeekday: DayOfWeek = DayOfWeek.MONDAY,
        ) = trainingCalendar(sessions, weeks, now, zone, firstWeekday)

        /** A finished session starting at [start] and lasting [minutes]. */
        private fun lasting(
            start: Instant,
            minutes: Int,
            id: String = start.toString(),
        ) = session(start, start + minutes.minutes, id)

        private fun onDay(
            daysBefore: Int,
            hour: Int = 9,
        ) = local(2026, 8, 12 - daysBefore, hour)

        @Test
        fun `returns the weeks asked for, oldest first, seven consecutive days each`() {
            val weeks = calendar(emptyList(), 4)

            assertEquals(4, weeks.size)
            assertTrue(weeks[0].weekStart < weeks[3].weekStart)
            weeks.forEach { week ->
                assertEquals(7, week.days.size)
                assertEquals(week.weekStart, week.days[0].date)
            }
            val allDays = weeks.flatMap { week -> week.days.map { it.date } }
            allDays.zipWithNext().forEach { (day, next) -> assertEquals(day.toEpochDays() + 1, next.toEpochDays()) }
        }

        @Test
        fun `starts each row on Monday when the calendar says so`() {
            assertEquals(DayOfWeek.MONDAY, calendar(emptyList(), 1)[0].days[0].date.dayOfWeek)
        }

        @Test
        fun `starts each row on Sunday when the calendar says so`() {
            val weeks = calendar(emptyList(), 1, firstWeekday = DayOfWeek.SUNDAY)

            assertEquals(DayOfWeek.SUNDAY, weeks[0].days[0].date.dayOfWeek)
        }

        // A double day is one cell, but two sessions: the cell's shade is the day's total time, and the
        // row's count is what the THIS WEEK tile counts.
        @Test
        fun `sums a day with two sessions into one cell, and counts both`() {
            val weeks = calendar(listOf(lasting(onDay(0, 7), 20, "a"), lasting(onDay(0, 18), 25, "b")), 1)
            val today = weeks[0].days.single { it.isToday }

            assertEquals(listOf(2, 45), listOf(today.sessions, today.minutes))
            assertEquals(CalendarLevel.Long, today.level)
            assertEquals(listOf(2, 45), listOf(weeks[0].sessions, weeks[0].minutes))
        }

        /** The row's spoken time is the same whole-hours reading the THIS WEEK tile prints. */
        @Test
        fun `reads a week's time as whole hours and the minutes left over`() {
            val week = calendar(listOf(lasting(onDay(0, 7), 50, "a"), lasting(onDay(1), 45, "b")), 1)[0]

            assertEquals(HoursMinutes(hours = 1, minutes = 35), week.time)
        }

        @Test
        fun `shades by fixed minute buckets`() {
            assertEquals(
                listOf(
                    CalendarLevel.Short,
                    CalendarLevel.Short,
                    CalendarLevel.Mid,
                    CalendarLevel.Mid,
                    CalendarLevel.Long,
                    CalendarLevel.Long,
                ),
                listOf(0, 29, 30, 44, 45, 120).map { calendarLevel(1, it) },
            )
            assertEquals(CalendarLevel.Rest, calendarLevel(0, 0))
        }

        // An unfinished session has no duration. Shading its day as rest would say you didn't turn up.
        @Test
        fun `shades a day with only an unfinished session as trained`() {
            val day = calendar(listOf(session(onDay(1), endedAt = null)), 1)[0].days.single { it.sessions == 1 }

            assertEquals(0, day.minutes)
            assertEquals(CalendarLevel.Short, day.level)
        }

        @Test
        fun `flags today, and the days after it as still to come`() {
            val days = calendar(emptyList(), 1)[0].days

            assertEquals(listOf(false, false, true, false, false, false, false), days.map { it.isToday })
            assertEquals(listOf(false, false, false, true, true, true, true), days.map { it.isFuture })
        }

        // The THIS WEEK tile and the calendar's last row show the same week, so they must count it the
        // same way, including a session that was never finished.
        @Test
        fun `counts the current week the way the THIS WEEK tile does`() {
            val sessions =
                listOf(
                    lasting(onDay(0), 30, "a"),
                    lasting(onDay(1), 40, "b"),
                    session(onDay(2), endedAt = null, id = "c"),
                    lasting(onDay(9), 30, "d"),
                )

            assertEquals(
                thisWeekStats(sessions, wednesday, zone, DayOfWeek.MONDAY).sessions,
                calendar(sessions, 4).last().sessions,
            )
        }

        @Test
        fun `ignores anything older than the window`() {
            val weeks = calendar(listOf(lasting(local(2026, 6, 3, 9), 30)), 4)

            assertTrue(weeks.all { it.sessions == 0 })
        }

        // Every cell is a whole calendar day even across a DST change, where a day is 23 or 25 hours.
        @Test
        fun `keeps every day a distinct calendar day across a DST change`() {
            val days = calendar(emptyList(), 12, now = local(2026, 11, 18, 12)).flatMap { it.days }

            assertEquals(days.size, days.map { it.date }.toSet().size)
            assertEquals(LocalDate(2026, 8, 31), days.first().date)
        }

        /** A session late in the evening is that evening's in the reader's zone, not the next UTC day's. */
        @Test
        fun `files a session under its day in the reader's zone`() {
            val lateTuesday = local(2026, 8, 11, 22)
            val days = calendar(listOf(lasting(lateTuesday, 30)), 1)[0].days

            assertEquals(LocalDate(2026, 8, 11), days.single { it.sessions == 1 }.date)
        }
    }
}
