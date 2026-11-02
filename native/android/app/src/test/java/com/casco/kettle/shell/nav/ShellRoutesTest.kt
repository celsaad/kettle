package com.casco.kettle.shell.nav

import com.casco.kettle.shell.sessioneditor.SESSION_EDITOR_PATH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flip gate on M5's writer screens (#231). The flips are passed in rather than read from the
 * build: the unit tests run on debug alone, so release's are only reachable this way.
 */
class ShellRoutesTest {
    private val settings = Screen.Native {}
    private val sessionEditor = Screen.Native {}
    private val writers =
        mapOf(
            Flip.Preferences to mapOf("/settings" to settings),
            Flip.Sessions to mapOf("/session-editor" to sessionEditor),
        )

    @Test
    fun `a gated path is native with its flip on, as in debug`() {
        val routes = routesFor(Flip.entries.toSet(), writers)

        assertSame(settings, routes.screenAt("/settings"))
        assertSame(sessionEditor, routes.screenAt("/session-editor"))
    }

    @Test
    fun `a gated path stays RN with its flip off, as in release`() {
        val routes = routesFor(emptySet(), writers)

        assertEquals(Screen.Rn, routes.screenAt("/settings"))
        assertEquals(Screen.Rn, routes.screenAt("/session-editor"))
    }

    @Test
    fun `each flip gates only its own paths`() {
        val routes = routesFor(setOf(Flip.Preferences), writers)

        assertSame(settings, routes.screenAt("/settings"))
        assertEquals(Screen.Rn, routes.screenAt("/session-editor"))
    }

    /** The session editor writes edited session files, so it is the sessions flip's to release (#239). */
    @Test
    fun `the session editor is behind the sessions flip`() {
        assertTrue(routesFor(setOf(Flip.Sessions)).screenAt(SESSION_EDITOR_PATH) is Screen.Native)
        assertEquals(Screen.Rn, routesFor(Flip.entries.toSet() - Flip.Sessions).screenAt(SESSION_EDITOR_PATH))
    }

    @Test
    fun `the read-only screens are native whatever the flips`() {
        val on = routesFor(Flip.entries.toSet(), writers).screenAt(Tab.History.path)

        assertTrue(on is Screen.Native)
        assertSame(on, routesFor(emptySet(), writers).screenAt(Tab.History.path))
    }
}
