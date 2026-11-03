package com.casco.kettle.shell.nav

import com.casco.kettle.shell.exerciseeditor.EXERCISE_EDITOR_PATH
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
    private val importScreen = Screen.Native {}
    private val writers = mapOf(Flip.Library to mapOf("/import" to importScreen))

    @Test
    fun `a gated path is native with its flip on, as in debug`() {
        val routes = routesFor(Flip.entries.toSet(), writers)

        assertSame(importScreen, routes.screenAt("/import"))
    }

    @Test
    fun `a gated path stays RN with its flip off, as in release`() {
        val routes = routesFor(emptySet(), writers)

        assertEquals(Screen.Rn, routes.screenAt("/import"))
    }

    /** A library writer (#241): native on `.dev`, RN's on Play until the library flip releases. */
    @Test
    fun `the exercise editor is native only with the library flip on`() {
        assertTrue(routesFor(setOf(Flip.Library)).screenAt(EXERCISE_EDITOR_PATH) is Screen.Native)
        assertEquals(Screen.Rn, routesFor(emptySet()).screenAt(EXERCISE_EDITOR_PATH))
    }

    /** The sessions flip has released (#240), so the session editor is native in release builds too. */
    @Test
    fun `the session editor is native whatever the flips`() {
        assertTrue(routesFor(emptySet()).screenAt(SESSION_EDITOR_PATH) is Screen.Native)
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
