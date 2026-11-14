package com.casco.kettle.shell.nav

import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.SessionParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    @Test
    fun `a path parses to its destination and query params`() {
        assertEquals(
            Route(Destination.ExerciseEditor, mapOf("id" to "pushups")),
            Route.parse("/exercise-editor?id=pushups"),
        )
        assertEquals(Route(Destination.Today), Route.parse("/"))
    }

    /** Every screen is native: a path nobody registered is a bug to drop, not RN's to draw. */
    @Test
    fun `a path no destination has parses to nothing`() {
        assertNull(Route.parse("/nowhere"))
        assertNull(Route.parse("/session-native"))
    }

    @Test
    fun `query values are decoded`() {
        assertEquals(
            mapOf("programId" to "base", "day" to "Day 1 & 2"),
            Route.parse("/program-detail?programId=base&day=Day%201%20%26%202")?.params,
        )
    }

    /** A link is anyone's to type: one that can't be decoded goes nowhere, rather than crashing the launch. */
    @Test
    fun `a malformed escape parses to nothing, not a throw`() {
        assertNull(Route.fromLink("kettle://session?workoutId=%zz"))
        assertNull(Route.fromLink("kettle://session?workoutId=bench%"))
        assertNull(Route.parse("/exercise-editor?id=%E2%82"))
        assertNull(Route.parse("/exercise-editor?id=%+1"))
        // Arabic-Indic digits are digits to isDigit(), and no hex digit to an escape.
        assertNull(Route.parse("/exercise-editor?id=%\u0663\u0663"))
    }

    /** A path's query, not a form: `+` is a plus, as in a program day named "Push+Pull". */
    @Test
    fun `a plus in a value stays a plus`() {
        assertEquals(mapOf("day" to "Push+Pull"), Route.parse("/program-detail?day=Push+Pull")?.params)
    }

    @Test
    fun `a route writes back out as the path it parses from`() {
        val routes =
            listOf(
                Route(Destination.Today),
                Route.of(Section.PROGRAMS),
                Route(Destination.ProgramDetail, mapOf("programId" to "base", "day" to "Day 1 & 2+")),
                Route(Destination.SessionEditor, mapOf("id" to "2026-11-09T23-29-54-817Z")),
            )
        for (route in routes) assertEquals(route, Route.parse(route.path))
    }

    @Test
    fun `a kettle link parses with the same parser, host and path alike`() {
        assertEquals(Route(Destination.Progress), Route.fromLink("kettle://progress"))
        assertEquals(Route(Destination.Progress), Route.fromLink("kettle:///progress"))
        assertEquals(Route(Destination.Today), Route.fromLink("kettle://"))
        assertEquals(
            Route(Destination.ExerciseProgress, mapOf("exerciseId" to "pushups")),
            Route.fromLink("kettle://exercise-progress?exerciseId=pushups"),
        )
    }

    @Test
    fun `a link of another scheme, or to nowhere, parses to nothing`() {
        assertNull(Route.fromLink("https://history"))
        assertNull(Route.fromLink("kettle://nowhere"))
    }

    @Test
    fun `a session link carries each of the three starts`() {
        assertEquals(
            RunnerStart.Planned(SessionParams.Workout("bench")),
            Route.fromLink("kettle://session?workoutId=bench")?.runnerStart,
        )
        assertEquals(
            RunnerStart.Planned(SessionParams.ProgramSlot("base", 2, "Day 1")),
            Route.fromLink("kettle://session?programId=base&week=2&day=Day%201")?.runnerStart,
        )
        assertEquals(RunnerStart.Adhoc, Route.fromLink("kettle://session?adhoc=1")?.runnerStart)
    }

    /** #303's resolver reads no start as the library's first workout, as RN's `kettle://session` did. */
    @Test
    fun `a session link with no params is the runner with no start`() {
        val route = Route.fromLink("kettle://session")

        assertEquals(Destination.Session, route?.destination)
        assertNull(route?.runnerStart)
    }

    @Test
    fun `only the sheet destinations are sheets`() {
        assertTrue(Route(Destination.Settings).sheet)
        assertFalse(Route(Destination.Progress).sheet)
        assertFalse(Route(Destination.Session).sheet)
    }

    /** #411: the four-tab shell's paths land on the tab and section that now hold their screens. */
    @Test
    fun `the old tabs' and Stats' links land on their new tab and section`() {
        val programs = Route.of(Section.PROGRAMS)
        assertEquals(programs, Route.fromLink("kettle://programs"))
        assertEquals(
            Route.of(Section.SESSIONS),
            Route.fromLink("kettle://history"),
        )
        assertEquals(Route.of(Section.OVERVIEW), Route.parse("/analytics"))
        // Its own section wins over one the link carried.
        assertEquals(programs, Route.parse("/programs?section=exercises"))
    }

    /** #414: Stats became Overview, so a stack saved on Stats names no section now and opens on the first. */
    @Test
    fun `a saved Stats section lands on Overview`() {
        assertEquals(Section.OVERVIEW, Route(Destination.Progress, mapOf(SECTION to "stats")).section(Tab.Progress))
    }

    @Test
    fun `a tab's section is the one its route names, else its first`() {
        assertEquals(Section.SESSIONS, Route.of(Section.SESSIONS).section(Tab.Progress))
        assertEquals(Section.OVERVIEW, Route(Destination.Progress).section(Tab.Progress))
        assertEquals(Section.WORKOUTS, Route(Destination.Library, mapOf(SECTION to "nowhere")).section(Tab.Library))
        assertNull(Route(Destination.Today).section(Tab.Today))
    }
}
