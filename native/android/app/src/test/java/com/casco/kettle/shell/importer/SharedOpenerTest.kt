package com.casco.kettle.shell.importer

import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Navigator
import com.casco.kettle.shell.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How a share reaches Import (#514): each as a route of its own, so a second share while the first's
 * Import shows opens a new one, and held behind a running session or a sheet until nothing covers the screen.
 */
class SharedOpenerTest {
    private val navigator = Navigator()
    private val inbox = SharedLibraryInbox()
    private val opener = SharedOpener(navigator, inbox)
    private val first = SharedLibrary.Text("version: 1 # first")
    private val second = SharedLibrary.Text("version: 1 # second")

    private fun shownToken() = navigator.sheet?.params?.get(IMPORT_SHARED_PARAM)

    /** An equal route would be no move, and the Import showing would never read the second share. */
    @Test
    fun `a second share opens as a route of its own, with its own library`() {
        opener.open(first)
        val firstRoute = navigator.sheet
        opener.open(second)

        assertEquals(Destination.Import, navigator.sheet?.destination)
        assertNotEquals(firstRoute, navigator.sheet)
        assertNull(inbox.take(firstRoute?.params?.get(IMPORT_SHARED_PARAM)))
        assertEquals(second, inbox.take(shownToken()))
    }

    @Test
    fun `a share during a session waits for the runner to close, then opens`() {
        navigator.openRunner(null)
        opener.open(first)
        opener.openHeld()
        assertNull(navigator.sheet)

        navigator.closeRunner()
        opener.openHeld()

        assertEquals(first, inbox.take(shownToken()))
        assertNull(inbox.held)
    }

    /** Done's Edit log opens the session editor as the runner closes: the share mustn't replace it. */
    @Test
    fun `a held share waits for a sheet the user opened, too`() {
        navigator.openRunner(null)
        opener.open(first)
        navigator.closeRunner()
        navigator.navigate(Route(Destination.SessionEditor, mapOf("id" to "s1")))
        opener.openHeld()
        assertEquals(Destination.SessionEditor, navigator.sheet?.destination)

        navigator.back()
        opener.openHeld()
        assertEquals(first, inbox.take(shownToken()))
    }

    /** The hold is the process's: an activity made again mid-session, with a new opener, still opens it. */
    @Test
    fun `a held share survives a new opener`() {
        navigator.openRunner(null)
        opener.open(first)
        navigator.closeRunner()

        SharedOpener(navigator, inbox).openHeld()

        assertEquals(first, inbox.take(shownToken()))
    }

    /** A session started from Program detail ends on it: the share opens over it, and back returns there. */
    @Test
    fun `a held share opens over the sheet the session started from`() {
        val program = Route(Destination.ProgramDetail, mapOf("id" to "p"))
        navigator.navigate(program)
        navigator.openRunner(null)
        opener.open(first)
        navigator.closeRunner()
        opener.openHeld()

        assertEquals(first, inbox.take(shownToken()))
        navigator.back()
        assertEquals(program, navigator.sheet)
    }

    /** A newer share replaces the held one's library: the held token would open an empty Import. */
    @Test
    fun `a share that opens straight away lets go of one held before it`() {
        navigator.openRunner(null)
        opener.open(first)
        navigator.closeRunner()
        navigator.push(Route(Destination.SessionEditor, mapOf("id" to "s1")))
        opener.open(second)
        assertEquals(second, inbox.take(shownToken()))

        navigator.back()
        opener.openHeld()

        assertNull(navigator.sheet)
    }
}
