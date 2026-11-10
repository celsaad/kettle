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
        assertEquals(Route(Destination.Workouts), Route.parse("/"))
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

    @Test
    fun `a kettle link parses with the same parser, host and path alike`() {
        assertEquals(Route(Destination.History), Route.fromLink("kettle://history"))
        assertEquals(Route(Destination.History), Route.fromLink("kettle:///history"))
        assertEquals(Route(Destination.Workouts), Route.fromLink("kettle://"))
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
        assertFalse(Route(Destination.History).sheet)
        assertFalse(Route(Destination.Session).sheet)
    }
}
