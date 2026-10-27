package com.casco.kettle.shell.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SheetTransitionTest {
    @Test
    fun `a report is on a tab, in the sheet, or on the runner`() {
        assertEquals(Place.Tab, RouteReport("/history", modal = false).place)
        assertEquals(Place.Sheet, RouteReport("/settings", modal = true).place)
        // Above the tabs but not a sheet: the runner, which RN still presents itself.
        assertEquals(Place.Runner, RouteReport("/session", modal = false).place)
    }

    @Test
    fun `a route message reads as a report`() {
        assertEquals(
            RouteReport("/settings", modal = true),
            RouteReport.from(mapOf("type" to "route", "pathname" to "/settings", "modal" to true)),
        )
    }

    /** A JS bundle from before the sheet sends no flag: it presents nothing as a sheet. */
    @Test
    fun `a route message without the modal flag is not a modal`() {
        assertEquals(
            RouteReport("/settings", modal = false),
            RouteReport.from(mapOf("type" to "route", "pathname" to "/settings")),
        )
    }

    @Test
    fun `a message that is not a route report reads as nothing`() {
        assertNull(RouteReport.from(mapOf("type" to "ready")))
        assertNull(RouteReport.from(mapOf("type" to "route", "pathname" to 42)))
    }

    @Test
    fun `a push from a tab slides the sheet up, and back slides it down`() {
        assertEquals(Transition.Open, transitionBetween(Place.Tab, Place.Sheet, "PUSH"))
        assertEquals(Transition.Close, transitionBetween(Place.Sheet, Place.Tab, "GO_BACK"))
        // An editor's own close and a delete's dismissTo.
        assertEquals(Transition.Close, transitionBetween(Place.Sheet, Place.Tab, "POP"))
        assertEquals(Transition.Close, transitionBetween(Place.Sheet, Place.Tab, "POP_TO"))
    }

    /** Settings → Import or Support, Stats → Exercise progress, Program detail → Program editor. */
    @Test
    fun `a push inside the sheet moves along the shared axis, and back reverses it`() {
        assertEquals(Transition.Forward, transitionBetween(Place.Sheet, Place.Sheet, "PUSH"))
        assertEquals(Transition.Backward, transitionBetween(Place.Sheet, Place.Sheet, "GO_BACK"))
    }

    /**
     * Program guide → Import replaces; the shell's own navigate messages dismiss everything (POP_TO_TOP)
     * and navigate. The app opens nothing by NAVIGATE itself.
     */
    @Test
    fun `a replace, and the shell's own steering, fade through in place`() {
        assertEquals(Transition.FadeThrough, transitionBetween(Place.Sheet, Place.Sheet, "REPLACE"))
        assertEquals(Transition.FadeIn, transitionBetween(Place.Tab, Place.Sheet, "NAVIGATE"))
        assertEquals(Transition.FadeOut, transitionBetween(Place.Sheet, Place.Tab, "POP_TO_TOP"))
    }

    /** A report with no action before it: a reload, or a link the app was opened with. */
    @Test
    fun `a move with no action to go by fades`() {
        assertEquals(Transition.FadeIn, transitionBetween(Place.Tab, Place.Sheet, null))
        assertEquals(Transition.FadeOut, transitionBetween(Place.Sheet, Place.Tab, null))
    }

    /** Program detail → session, the one way into the runner from a sheet. */
    @Test
    fun `the sheet grows into the runner, and shrinks back out of it`() {
        assertEquals(Transition.Grow, transitionBetween(Place.Sheet, Place.Runner, "PUSH"))
        assertEquals(Transition.Shrink, transitionBetween(Place.Runner, Place.Sheet, "GO_BACK"))
    }

    @Test
    fun `moves that never touch a sheet are RN's own`() {
        assertEquals(Transition.None, transitionBetween(Place.Tab, Place.Tab, "NAVIGATE"))
        assertEquals(Transition.None, transitionBetween(Place.Tab, Place.Runner, "PUSH"))
    }

    /** Finishing a session the sheet grew into lands on a tab: whatever was under the runner goes. */
    @Test
    fun `leaving the runner for a tab drops whatever sheet it covered`() {
        assertEquals(Transition.Release, transitionBetween(Place.Runner, Place.Tab, "POP_TO"))
    }
}
