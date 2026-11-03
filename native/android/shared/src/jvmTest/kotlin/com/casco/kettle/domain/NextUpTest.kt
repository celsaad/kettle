package com.casco.kettle.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * `nextWeekAfter` and `nextUpView`, case for case from RN's `next-up.test.ts`, which went with RN Home
 * (#235). Where jest builds its dates in the machine's zone, these pass New York, so the calendar-day
 * arithmetic runs in a real zone on every machine.
 */
class NextUpTest {
    private val zone = TimeZone.of("America/New_York")

    private fun session(
        program: String?,
        week: Long?,
        day: String? = null,
        startedAt: Instant = Instant.parse("2026-07-01T09:00:00Z"),
    ) = Session(1, startedAt.toString(), "w", program, week, day, startedAt.toString(), null, emptyList())

    @Nested
    inner class NextWeekAfter {
        private val program =
            Program("prog", "Prog", (1L..3L).map { ProgramWeek.Training(it, workoutId = "full") })

        @Test
        fun `starts at the first week when there is no prior session`() {
            assertEquals(1L, nextWeekAfter(program, emptyList()).week)
        }

        @Test
        fun `picks the next week after the most recent tracked session`() {
            assertEquals(2L, nextWeekAfter(program, listOf(session("prog", 1))).week)
        }

        @Test
        fun `wraps back to the first week after the last one`() {
            assertEquals(1L, nextWeekAfter(program, listOf(session("prog", 3))).week)
        }

        @Test
        fun `falls back to the first week when the referenced week no longer exists in the program`() {
            assertEquals(1L, nextWeekAfter(program, listOf(session("prog", 99))).week)
        }

        @Test
        fun `ignores a session belonging to a different program`() {
            assertEquals(1L, nextWeekAfter(program, listOf(session("other-prog", 2))).week)
        }

        // The literal next slot: whether a rest slot is still owed is nextUpView's, which has a clock.
        @Test
        fun `returns a rest slot as the literal next one, without skipping it`() {
            val withRest =
                Program(
                    "prog",
                    "Prog",
                    listOf(ProgramWeek.Training(1, "Day 1", "full"), ProgramWeek.RestDay(1, "Day 2")),
                )

            assertTrue(nextWeekAfter(withRest, listOf(session("prog", 1, "Day 1"))) is ProgramWeek.RestDay)
        }
    }

    /**
     * The days of one week number run in the order they were written: `day` is free user text. These
     * pin RN's fix of a `localeCompare` tiebreak that ran a calendar week Friday → Monday → Saturday.
     */
    @Nested
    inner class TheOrderTheDaysOfAWeekRunIn {
        private fun weekOf(days: List<String>) =
            Program("prog", "Prog", days.map { ProgramWeek.Training(1, it, "full") })

        private fun after(
            program: Program,
            day: String,
        ) = nextWeekAfter(program, listOf(session("prog", 1, day))).day

        private val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

        @Test
        fun `opens a calendar week on Monday rather than on whichever day sorts first`() {
            assertEquals("Monday", nextWeekAfter(weekOf(weekdays), emptyList()).day)
        }

        @Test
        fun `advances a calendar week one weekday at a time`() {
            val program = weekOf(weekdays)

            assertEquals("Tuesday", after(program, "Monday"))
            assertEquals("Sunday", after(program, "Saturday"))
        }

        // Numbered labels, the seed's old workaround, still broke past nine as text.
        @Test
        fun `counts numbered days numerically past nine`() {
            val program = weekOf((1..10).map { "Day $it" })

            assertEquals("Day 2", after(program, "Day 1"))
            assertEquals("Day 10", after(program, "Day 9"))
        }

        // Pins the mechanism: a sort that stopped keeping equal elements in input order fails here.
        @Test
        fun `preserves file order for labels with no order of their own`() {
            val program = weekOf(listOf("C", "A", "B"))

            assertEquals("C", nextWeekAfter(program, emptyList()).day)
            assertEquals("A", after(program, "C"))
        }

        @Test
        fun `still orders by week number first, whatever the days are called`() {
            val program =
                Program(
                    "prog",
                    "Prog",
                    listOf(ProgramWeek.Training(2, "Monday", "full"), ProgramWeek.Training(1, "Tuesday", "full")),
                )

            assertEquals(1L, nextWeekAfter(program, emptyList()).week)
        }
    }

    /**
     * How a rest day clears itself off Home. Nothing about one is logged, so elapsed calendar days move
     * it along: with `R` consecutive rest slots, `restsServed = max(0, daysSince(lastSession) - 1)`.
     */
    @Nested
    inner class RestDays {
        private val library =
            Library(
                1,
                emptyList(),
                listOf(Workout("push", "Push", emptyList()), Workout("pull", "Pull", emptyList())),
                listOf(
                    Program(
                        "prog",
                        "Prog",
                        listOf(
                            ProgramWeek.Training(1, "Day 1", "push"),
                            ProgramWeek.RestDay(1, "Day 2", "Walk."),
                            ProgramWeek.Training(1, "Day 3", "pull"),
                        ),
                    ),
                ),
            )

        private fun withWeeks(vararg weeks: ProgramWeek) =
            library.copy(programs = listOf(Program("prog", "Prog", weeks.toList())))

        /** July [day] 2026 at [hour] in [zone], as jest's `new Date(2026, 6, day, hour)`. */
        private fun july(
            day: Int,
            hour: Int,
        ): Instant = LocalDateTime(2026, 7, day, hour, 0).toInstant(zone)

        private val monday = july(27, 18)

        /** 9:00 [days] calendar days after Monday, stepped by date as jest's `new Date` rolls July 57 over. */
        private fun dayAfter(days: Int) =
            LocalDate(2026, 7, 27).plus(DatePeriod(days = days)).atTime(9, 0).toInstant(zone)

        private fun trainedOn(at: Instant) = listOf(session("prog", 1, "Day 1", at))

        private fun view(
            at: Instant,
            of: Library = library,
            sessions: List<Session> = trainedOn(monday),
        ) = nextUpView(of, sessions, at, zone)

        @Test
        fun `shows the rest day on the evening of the session before it`() {
            val rest = view(monday) as NextUp.Rest

            assertEquals("Day 2", rest.weekDay)
            assertEquals("Walk.", rest.weekNotes)
        }

        @Test
        fun `still shows it the next day, which is the day being rested`() {
            assertTrue(view(dayAfter(1)) is NextUp.Rest)
        }

        @Test
        fun `moves on to the next workout the day after that`() {
            val queued = view(dayAfter(2)) as NextUp.Queued

            assertEquals("pull", queued.workout.id)
            assertEquals("Day 3", queued.weekDay)
        }

        @Test
        fun `stays moved on after a long layoff rather than re-owing the rest day`() {
            assertTrue(view(dayAfter(30)) is NextUp.Queued)
        }

        @Test
        fun `offers the next workout as an escape hatch while resting`() {
            assertEquals(SessionParams.ProgramSlot("prog", 1, "Day 3"), (view(monday) as NextUp.Rest).skipTo)
        }

        @Test
        fun `spends two consecutive rest slots over two days`() {
            val twoRests =
                withWeeks(
                    ProgramWeek.Training(1, "Day 1", "push"),
                    ProgramWeek.RestDay(1, "Day 2"),
                    ProgramWeek.RestDay(1, "Day 3"),
                    ProgramWeek.Training(1, "Day 4", "pull"),
                )

            assertEquals("Day 2", (view(dayAfter(1), twoRests) as NextUp.Rest).weekDay)
            assertEquals("Day 3", (view(dayAfter(2), twoRests) as NextUp.Rest).weekDay)
            assertTrue(view(dayAfter(3), twoRests) is NextUp.Queued)
        }

        // No session tracked against the program means no anchor to count days from, so it shows the
        // rest day rather than guessing, and "train anyway" is the way out.
        @Test
        fun `shows a leading rest day when the program has never been run`() {
            val leadingRest = withWeeks(ProgramWeek.RestDay(1, "Day 1"), ProgramWeek.Training(1, "Day 2", "push"))

            val rest = view(monday, leadingRest, emptyList()) as NextUp.Rest

            assertEquals("Day 1", rest.weekDay)
            assertEquals(SessionParams.ProgramSlot("prog", 1, "Day 2"), rest.skipTo)
        }

        // The schema refuses it on import, but the in-app editor can build it, and a link to nowhere is
        // worse than no link.
        @Test
        fun `leaves the escape hatch empty when nothing in the program runs anything`() {
            val allRest = withWeeks(ProgramWeek.RestDay(1))

            assertNull((view(monday, allRest, emptyList()) as NextUp.Rest).skipTo)
        }

        @Test
        fun `wraps a trailing rest day round to the next cycle`() {
            val trailingRest = withWeeks(ProgramWeek.Training(1, "Day 1", "push"), ProgramWeek.RestDay(1, "Day 2"))

            assertTrue(view(dayAfter(1), trailingRest) is NextUp.Rest)
            // Finishing a program restarts it.
            assertEquals("Day 1", (view(dayAfter(2), trailingRest) as NextUp.Queued).weekDay)
        }

        // A day boundary in local time, not 24 hours: 23:00 to 01:00 the next morning is a day gone by.
        @Test
        fun `counts calendar days in the reader's zone rather than elapsed hours`() {
            val lateSession = listOf(session("prog", 1, "Day 1", july(27, 23)))

            assertTrue(view(july(29, 1), sessions = lateSession) is NextUp.Queued)
        }
    }

    @Nested
    inner class WithoutAProgram {
        private val push = Workout("push", "Push", emptyList())
        private val pull = Workout("pull", "Pull", emptyList())

        @Test
        fun `queues nothing for a library with no workouts`() {
            assertNull(
                nextUpView(Library(1, emptyList(), emptyList(), emptyList()), emptyList(), Instant.DISTANT_PAST, zone),
            )
        }

        // The pick rotates by UTC day, so it holds still through a day rather than flickering.
        @Test
        fun `picks a library workout by the day, with a bare workout id to start`() {
            val library = Library(1, emptyList(), listOf(push, pull), emptyList())
            val day = Instant.fromEpochMilliseconds(20_000L * 86_400_000)

            val today = nextUpView(library, emptyList(), day, zone) as NextUp.Queued
            val tomorrow = nextUpView(library, emptyList(), Instant.fromEpochMilliseconds(20_001L * 86_400_000), zone)

            assertEquals(push, today.workout)
            assertNull(today.weekNumber)
            assertEquals(SessionParams.Workout("push"), today.sessionParams)
            assertEquals(pull, (tomorrow as NextUp.Queued).workout)
        }

        @Test
        fun `falls back to a library workout when the program's slot names one that is gone`() {
            val library =
                Library(
                    1,
                    emptyList(),
                    listOf(push),
                    listOf(Program("p", "P", listOf(ProgramWeek.Training(1, workoutId = "gone")))),
                )

            assertEquals(
                SessionParams.Workout("push"),
                (nextUpView(library, emptyList(), Instant.DISTANT_PAST, zone) as NextUp.Queued).sessionParams,
            )
        }

        // The program the newest program session ran under, not the library's first.
        @Test
        fun `follows the program the latest session ran under`() {
            val library =
                Library(
                    1,
                    emptyList(),
                    listOf(push, pull),
                    listOf(
                        Program("a", "A", listOf(ProgramWeek.Training(1, workoutId = "push"))),
                        Program("b", "B", listOf(ProgramWeek.Training(1, workoutId = "pull", notes = "Easy."))),
                    ),
                )

            val queued = nextUpView(library, listOf(session("b", 1)), Instant.DISTANT_PAST, zone) as NextUp.Queued

            assertEquals(pull, queued.workout)
            assertEquals(1L, queued.weekNumber)
            assertEquals("Easy.", queued.weekNotes)
            assertEquals(SessionParams.ProgramSlot("b", 1, null), queued.sessionParams)
        }
    }
}
