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
    private val sessionEditor = Screen.Native {}
    private val importScreen = Screen.Native {}
    private val writers =
        mapOf(
            Flip.Sessions to mapOf("/session-editor" to sessionEditor),
            Flip.Library to mapOf("/import" to importScreen),
        )

    @Test
    fun `a gated path is native with its flip on, as in debug`() {
        val routes = routesFor(Flip.entries.toSet(), writers)

        assertSame(sessionEditor, routes.screenAt("/session-editor"))
        assertSame(importScreen, routes.screenAt("/import"))
    }

    @Test
    fun `a gated path stays RN with its flip off, as in release`() {
        val routes = routesFor(emptySet(), writers)

        assertEquals(Screen.Rn, routes.screenAt("/session-editor"))
        assertEquals(Screen.Rn, routes.screenAt("/import"))
    }

    @Test
    fun `each flip gates only its own paths`() {
        val routes = routesFor(setOf(Flip.Sessions), writers)

        assertSame(sessionEditor, routes.screenAt("/session-editor"))
        assertEquals(Screen.Rn, routes.screenAt("/import"))
    }

    /** The session editor writes edited session files, so it is the sessions flip's to release (#239). */
    @Test
    fun `the session editor is behind the sessions flip`() {
        assertTrue(routesFor(setOf(Flip.Sessions)).screenAt(SESSION_EDITOR_PATH) is Screen.Native)
        assertEquals(Screen.Rn, routesFor(Flip.entries.toSet() - Flip.Sessions).screenAt(SESSION_EDITOR_PATH))
    }

    /** The preferences flip has released (#238), so Settings is native in release builds too. */
    @Test
    fun `Settings is native whatever the flips`() {
        assertTrue(routesFor(emptySet()).screenAt("/settings") is Screen.Native)
    }

    @Test
    fun `the read-only screens are native whatever the flips`() {
        val on = routesFor(Flip.entries.toSet(), writers).screenAt(Tab.History.path)

        assertTrue(on is Screen.Native)
        assertSame(on, routesFor(emptySet(), writers).screenAt(Tab.History.path))
    }
}
